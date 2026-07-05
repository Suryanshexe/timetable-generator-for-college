import java.util.*;

public class AIService {
    private static final int MAX_RETRIES = 5;

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
            String scheduleType
    ) throws Exception {
        String systemPrompt = PromptBuilder.buildSystemPrompt(scheduleType);
        String userPrompt = PromptBuilder.buildUserPrompt(courses, faculty, rooms, dept, semester, section, constraints, customCommand, days, slots);

        Map<String, Object> parsedResponse = null;
        List<Map<String, Object>> generatedTimetable = null;
        ValidationEngine.ValidationResult validation = null;

        String currentPrompt = userPrompt;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("[AIService] Attempt " + attempt + " generating timetable...");
            String aiResponse = GroqClient.callGroq(systemPrompt, currentPrompt);
            
            try {
                parsedResponse = ResponseParser.parseResponse(aiResponse);
                generatedTimetable = (List<Map<String, Object>>) parsedResponse.get("timetable");
                if (generatedTimetable == null) {
                    throw new RuntimeException("Timetable array is missing in response");
                }
            } catch (Exception e) {
                System.err.println("[AIService] JSON parsing or structure failed. Error: " + e.getMessage());
                currentPrompt = "Your previous response was not valid JSON or was missing the 'timetable' key. " +
                        "Please output ONLY valid JSON matching the schema.\n" + userPrompt;
                continue;
            }

            validation = ValidationEngine.validate(generatedTimetable, courses, faculty, rooms);

            // Aim for 0 hard and 0 soft violations in first 3 attempts, fallback to 0 hard violations on later attempts
            if (attempt <= 3) {
                if (validation.hardCount == 0 && validation.softCount == 0) {
                    System.out.println("[AIService] Validation succeeded! 0 Hard and 0 Soft Constraint Violations in attempt " + attempt);
                    break;
                }
            } else {
                if (validation.hardCount == 0) {
                    System.out.println("[AIService] Validation succeeded! 0 Hard Constraint Violations in attempt " + attempt);
                    break;
                }
            }

            System.out.println("[AIService] Attempt " + attempt + " failed with " + validation.hardCount + " hard conflicts and " + validation.softCount + " soft conflicts.");
            
            // Build the repair prompt
            StringBuilder repairMsg = new StringBuilder();
            repairMsg.append("Your generated timetable had the following issues:\n");
            if (validation.hardCount > 0) {
                repairMsg.append("- Hard Constraint Violations:\n").append(JsonUtil.toJson(validation.conflicts)).append("\n");
            }
            if (validation.softCount > 0) {
                repairMsg.append("- Soft Constraint Violations:\n").append(JsonUtil.toJson(validation.softViolations)).append("\n");
            }
            repairMsg.append("\nPlease correct all of the above constraint violations. Ensure there are ZERO clashes, and that no classes are placed during lunch break (12:00-1:00) or Saturday if avoidSaturday is true.\n");
            repairMsg.append("Here is the timetable you generated in the previous step:\n").append(JsonUtil.toJson(generatedTimetable));

            currentPrompt = repairMsg.toString();
            
            // Sleep to avoid Groq TPM rate limits
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        if (validation == null) {
            throw new RuntimeException("Failed to generate timetable using AI after " + MAX_RETRIES + " attempts.");
        }

        // Re-inject final verified validation analytics to prevent AI hallucination of scores
        parsedResponse.put("conflicts", validation.conflicts);
        parsedResponse.put("softConstraintViolations", validation.softViolations);
        parsedResponse.put("facultyWorkload", TimetableController.calculateFacultyWorkload(generatedTimetable, faculty));
        parsedResponse.put("roomUtilization", TimetableController.calculateRoomUtilization(generatedTimetable, rooms));

        Map<String, Object> scoreMap = new LinkedHashMap<>();
        scoreMap.put("hardViolations", validation.hardCount);
        scoreMap.put("softViolations", validation.softCount);
        scoreMap.put("overallScore", validation.overallScore);
        parsedResponse.put("score", scoreMap);
        parsedResponse.put("generator", "AI (Groq Llama 3.3)");

        return JsonUtil.toJson(parsedResponse);
    }
}
