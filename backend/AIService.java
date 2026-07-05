import java.util.*;

/**
 * AI-driven timetable generation using Groq Llama 3.3.
 * Implements a validation-repair loop: generate → validateFull (cross-semester) → repair.
 * Falls back to deterministic local solver if AI fails.
 *
 * Key improvements:
 * - Passes the FULL existing timetable to AI so it avoids cross-semester clashes
 * - Uses validateFull() which checks room/faculty conflicts across ALL semesters
 * - Detects faculty overload and generates staffing alerts
 * - Room capacity is enforced at validation and repair stages
 */
public class AIService {
    private static final int MAX_RETRIES = 5;

    /**
     * Generate a timetable for the given dept+semester+section.
     *
     * @param existingTimetable  All already-saved entries (other semesters/depts) — used for
     *                           cross-semester clash detection. Pass empty list if none.
     */
    @SuppressWarnings("unchecked")
    public static String generate(
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms,
            String dept, String semester, String section,
            Map<String, Object> constraints,
            String customCommand,
            List<String> days,
            List<String> slots,
            String scheduleType,
            List<Map<String, Object>> existingTimetable   // ← NEW: cross-semester context
    ) throws Exception {

        String systemPrompt = PromptBuilder.buildSystemPrompt(scheduleType);

        // Build user prompt WITH cross-semester context
        String userPrompt = PromptBuilder.buildUserPromptWithContext(
                courses, faculty, rooms, dept, semester, section,
                constraints, customCommand, days, slots,
                existingTimetable);

        Map<String, Object>          parsedResponse     = null;
        List<Map<String, Object>>    generatedTimetable = null;
        ValidationEngine.ValidationResult validation    = null;
        String currentUserPrompt = userPrompt;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("[AIService] === Attempt " + attempt + "/" + MAX_RETRIES + " ===");

            // ── Call Groq ────────────────────────────────────────────────────
            String aiResponse;
            try {
                aiResponse = GroqClient.callGroq(systemPrompt, currentUserPrompt);
            } catch (Exception e) {
                System.err.println("[AIService] Groq call failed (attempt " + attempt + "): " + e.getMessage());
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(2000L * attempt);
                continue;
            }

            // ── Parse JSON ───────────────────────────────────────────────────
            try {
                parsedResponse = ResponseParser.parseResponse(aiResponse);
            } catch (Exception e) {
                System.err.println("[AIService] JSON parse failed (attempt " + attempt + "): " + e.getMessage());
                currentUserPrompt =
                    "Your previous response was NOT valid JSON. Error: " + e.getMessage() + "\n\n" +
                    "Output ONLY a valid JSON object with this structure:\n" +
                    "{\"timetable\":[{\"day\":\"Monday\",\"slot\":\"9:00-10:00\",\"course\":\"CS301\"," +
                    "\"faculty\":\"F01\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5," +
                    "\"dept\":\"CSE\",\"color\":\"#1a4a8a\"}]}\n\n" +
                    "Original task:\n" + userPrompt;
                Thread.sleep(1500L);
                continue;
            }

            // ── Extract timetable array ───────────────────────────────────────
            generatedTimetable = extractTimetableArray(parsedResponse);
            if (generatedTimetable == null || generatedTimetable.isEmpty()) {
                System.err.println("[AIService] Attempt " + attempt + ": missing/empty 'timetable' key. " +
                        "Response keys: " + parsedResponse.keySet());
                currentUserPrompt =
                    "Your response is missing the required 'timetable' array (or it is empty).\n" +
                    "REQUIRED: the JSON root object MUST contain a 'timetable' key with a non-empty array.\n\n" +
                    "Original task:\n" + userPrompt;
                Thread.sleep(1500L);
                continue;
            }

            // ── Normalize data types ──────────────────────────────────────────
            generatedTimetable = normalizeTimetableEntries(generatedTimetable, dept, semester);

            // ── Validate cross-semester ───────────────────────────────────────
            validation = ValidationEngine.validateFull(
                    generatedTimetable,
                    existingTimetable,   // ← pass all other semester entries
                    courses, faculty, rooms);

            System.out.printf("[AIService] Attempt %d: %d entries | %d hard | %d soft | score=%d%%%n",
                    attempt, generatedTimetable.size(),
                    validation.hardCount, validation.softCount, validation.overallScore);

            // ── Success? ──────────────────────────────────────────────────────
            // First 3 attempts: require 0 hard + 0 soft violations
            // Attempts 4-5: accept 0 hard only (soft is OK)
            boolean success = attempt <= 3
                    ? (validation.hardCount == 0 && validation.softCount == 0)
                    : (validation.hardCount == 0);

            if (success) {
                System.out.println("[AIService] ✓ SUCCESS on attempt " + attempt);
                break;
            }

            // ── Build repair prompt ───────────────────────────────────────────
            if (attempt < MAX_RETRIES) {
                currentUserPrompt = PromptBuilder.buildRepairPrompt(
                        userPrompt,
                        JsonUtil.toJson(generatedTimetable),
                        validation.conflicts,
                        validation.softViolations);
                System.out.println("[AIService] Sending repair prompt with " +
                        validation.hardCount + " hard violations to fix...");
                Thread.sleep(2500L);
            }
        }

        if (generatedTimetable == null) {
            throw new RuntimeException(
                    "AI failed to generate a valid timetable after " + MAX_RETRIES + " attempts.");
        }
        if (validation == null) {
            validation = ValidationEngine.validateFull(
                    generatedTimetable, existingTimetable, courses, faculty, rooms);
        }

        // ── Build final response ──────────────────────────────────────────────
        // Overwrite AI self-reported scores with our validated scores
        parsedResponse.put("timetable",               generatedTimetable);
        parsedResponse.put("conflicts",               validation.conflicts);
        parsedResponse.put("softConstraintViolations",validation.softViolations);
        parsedResponse.put("staffingAlerts",          validation.staffingAlerts);
        parsedResponse.put("facultyWorkload",
                TimetableController.calculateFacultyWorkload(generatedTimetable, faculty));
        parsedResponse.put("roomUtilization",
                TimetableController.calculateRoomUtilization(generatedTimetable, rooms));

        Map<String, Object> scoreMap = new LinkedHashMap<>();
        scoreMap.put("hardViolations", validation.hardCount);
        scoreMap.put("softViolations", validation.softCount);
        scoreMap.put("overallScore",   validation.overallScore);
        parsedResponse.put("score",      scoreMap);
        parsedResponse.put("generator", "AI (Groq Llama 3.3-70B Versatile)");
        parsedResponse.put("entries",    generatedTimetable.size());

        return JsonUtil.toJson(parsedResponse);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractTimetableArray(Map<String, Object> parsed) {
        for (String key : new String[]{"timetable", "schedule", "entries", "result", "data"}) {
            Object val = parsed.get(key);
            if (val instanceof List<?> list && !list.isEmpty()) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map) result.add((Map<String, Object>) item);
                }
                if (!result.isEmpty()) {
                    if (!"timetable".equals(key)) {
                        System.out.println("[AIService] Note: timetable found under key '" + key + "', normalised.");
                    }
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Normalize entry data types:
     *  - semester → int
     *  - section  → String
     *  - dept     → fallback to provided dept
     *  - color    → default if missing
     */
    private static List<Map<String, Object>> normalizeTimetableEntries(
            List<Map<String, Object>> entries, String dept, String semester) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        int semInt = 0;
        try { semInt = Integer.parseInt(semester); } catch (NumberFormatException ignored) {}

        for (Map<String, Object> entry : entries) {
            Map<String, Object> e = new LinkedHashMap<>(entry);

            // semester → int
            Object sem = e.get("semester");
            if (sem instanceof String s) {
                try { e.put("semester", Integer.parseInt(s.trim())); }
                catch (NumberFormatException ex) { e.put("semester", semInt); }
            } else if (sem instanceof Number n) {
                e.put("semester", n.intValue());
            } else {
                e.put("semester", semInt);
            }

            // dept fallback
            if (e.get("dept") == null || e.get("dept").toString().isBlank()) {
                e.put("dept", dept);
            }

            // section → String
            Object sec = e.get("section");
            if (sec != null) e.put("section", sec.toString());

            // color default
            if (e.get("color") == null || e.get("color").toString().isBlank()) {
                e.put("color", "#1a4a8a");
            }

            normalized.add(e);
        }
        return normalized;
    }
}
