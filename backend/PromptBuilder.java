import java.util.List;
import java.util.Map;

/**
 * Builds structured, detailed prompts for the Groq Llama 3.3 timetable generation engine.
 * Includes cross-semester context, room capacity rules, and staffing awareness.
 */
public class PromptBuilder {

    public static String buildSystemPrompt(String scheduleType) {
        boolean isEasy = "easy".equalsIgnoreCase(scheduleType);

        return """
                You are an expert University Timetable Scheduling Engine with full awareness of all semesters.
                Your ONLY output must be a single valid JSON object. Never add explanations or commentary outside the JSON.

                ═══════════════════════════════════════════════════════════
                HARD CONSTRAINTS — violating any of these is UNACCEPTABLE:
                ═══════════════════════════════════════════════════════════
                1.  FACULTY CLASH: A faculty member CANNOT teach two classes at the same day+slot —
                    this applies ACROSS ALL SEMESTERS and departments, not just the target semester.
                2.  ROOM CLASH: A room CANNOT host two classes at the same day+slot —
                    this applies ACROSS ALL SEMESTERS. Check 'existingTimetable' for already-occupied slots.
                3.  STUDENT CLASH: A section CANNOT have two classes at the same day+slot.
                4.  LAB IN LAB ROOM: All lab courses (type=Lab) MUST use rooms with type=Lab only.
                5.  THEORY IN LECTURE ROOM: Theory courses MUST use rooms with type=Lecture or Seminar only.
                6.  CONTIGUOUS LABS: Lab sessions MUST be in consecutive slots (e.g., 9:00-10:00 + 10:00-11:00)
                    on the same day. Never split a lab block across non-consecutive slots.
                7.  LUNCH BREAK: ABSOLUTELY NO class may be placed in the 12:00-1:00 slot.
                8.  COMPLETE HOURS: Each course must be scheduled for exactly (credits) hours per week.
                9.  VALID REFERENCES: Only use IDs from the provided courses, faculty, and rooms lists.
                10. FACULTY AVAILABILITY: Do not schedule a faculty member in their 'unavailableSlots'.
                11. ROOM CAPACITY: NEVER assign a course to a room whose capacity < student count of the course.
                    Always check: room.capacity >= course.studentsCount

                ═══════════════════════════════════════════════════════════
                HOW TO HANDLE 'existingTimetable':
                ═══════════════════════════════════════════════════════════
                The 'existingTimetable' field contains ALL already-scheduled entries (other semesters/depts).
                You MUST treat these as occupied slots:
                  - If a room is in existingTimetable at day X, slot Y → that room is UNAVAILABLE at X+Y
                  - If a faculty is in existingTimetable at day X, slot Y → that faculty is UNAVAILABLE at X+Y
                Build a mental occupancy grid BEFORE placing any entry.

                ═══════════════════════════════════════════════════════════
                STAFFING RULES:
                ═══════════════════════════════════════════════════════════
                - If a faculty member's total weekly hours (new + existing) would exceed their maxHoursPerDay × 5,
                  DO NOT overload them. Instead, add a 'staffingAlerts' array to your response with:
                  {"faculty": "F01", "issue": "overloaded", "recommendation": "Recruit additional staff for <courses>"}
                - If no other faculty can teach a course, still schedule it but include the staffing alert.

                """ + (isEasy ? """
                SOFT CONSTRAINTS (EASY MODE):
                - Leave at least 1 empty slot between any two consecutive classes for the same faculty or section.
                - Avoid scheduling 3+ classes back-to-back for any faculty member.
                """ : """
                SOFT CONSTRAINTS (TIGHT MODE):
                - Pack classes efficiently with minimal idle gaps for students.
                - Distribute classes across multiple days (avoid putting all lectures on a single day).
                """) + """
                - ROOM DISTRIBUTION: Spread classes across all available rooms.
                - AVOID LATE CLASSES: Minimize 4:00-5:00 PM usage.
                - WORKLOAD BALANCE: Distribute faculty teaching hours evenly across Mon–Fri.
                - AVOID SATURDAY: Prefer Mon–Fri. Use Saturday only if unavoidable.
                - MINIMIZE IDLE GAPS: Reduce empty periods between classes for student sections.

                ═══════════════════════════════════════════════════════════
                REQUIRED OUTPUT JSON SCHEMA:
                ═══════════════════════════════════════════════════════════
                {
                  "timetable": [
                    {
                      "day": "Monday",
                      "slot": "9:00-10:00",
                      "course": "CS301",
                      "faculty": "F01",
                      "room": "R101",
                      "section": "A",
                      "semester": 5,
                      "dept": "CSE",
                      "color": "#1a4a8a"
                    }
                  ],
                  "staffingAlerts": [],
                  "conflicts": [],
                  "score": {
                    "hardViolations": 0,
                    "softViolations": 0,
                    "overallScore": 100
                  }
                }

                COLOR RULES: Assign one consistent hex color per unique course ID.
                Use: ["#1a4a8a","#c0392b","#1a7a46","#b7620a","#5a2d82","#0d6e7a","#e67e22","#2ecc71","#3498db","#8e44ad","#16a085","#d35400"]

                CRITICAL: Output ONLY valid JSON. No markdown, no code fences, no extra text.
                semester field = INTEGER. section field = STRING. Each entry = 1 hour slot.
                """;
    }

    public static String buildUserPrompt(
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms,
            String dept, String semester, String section,
            Map<String, Object> constraints,
            String customCommand,
            List<String> days,
            List<String> slots
    ) {
        return buildUserPromptWithContext(
                courses, faculty, rooms, dept, semester, section,
                constraints, customCommand, days, slots,
                List.of() // no existing timetable context
        );
    }

    /**
     * Build user prompt including the full existing timetable for cross-semester awareness.
     */
    public static String buildUserPromptWithContext(
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms,
            String dept, String semester, String section,
            Map<String, Object> constraints,
            String customCommand,
            List<String> days,
            List<String> slots,
            List<Map<String, Object>> existingTimetable
    ) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("targetDept", dept);
        payload.put("targetSemester", Integer.parseInt(semester));
        payload.put("targetSection", section);
        payload.put("availableDays", days);
        payload.put("availableSlots", slots);
        payload.put("constraints", constraints);
        payload.put("courses", courses);
        payload.put("faculty", faculty);
        payload.put("rooms", rooms);

        if (!existingTimetable.isEmpty()) {
            payload.put("existingTimetable", existingTimetable);
        }

        if (customCommand != null && !customCommand.isBlank()) {
            payload.put("additionalInstruction", customCommand);
        }

        // Build occupancy summary for AI (concise version to save tokens)
        if (!existingTimetable.isEmpty()) {
            java.util.Map<String, java.util.Set<String>> occupiedRooms   = new java.util.LinkedHashMap<>();
            java.util.Map<String, java.util.Set<String>> occupiedFaculty = new java.util.LinkedHashMap<>();
            for (Map<String, Object> entry : existingTimetable) {
                if (entry == null) continue;
                String d = ValidationEngine.safeStr(entry, "day");
                String s = ValidationEngine.safeStr(entry, "slot");
                String r = ValidationEngine.safeStr(entry, "room");
                String f = ValidationEngine.safeStr(entry, "faculty");
                if (d == null || s == null) continue;
                String key = d + " " + s;
                if (r != null) occupiedRooms.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>()).add(r);
                if (f != null) occupiedFaculty.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>()).add(f);
            }
            payload.put("occupiedRoomsBySlot",   occupiedRooms);
            payload.put("occupiedFacultyBySlot", occupiedFaculty);
        }

        // Human-readable task summary
        StringBuilder summary = new StringBuilder();
        summary.append("Generate a complete weekly timetable for:\n");
        summary.append("  Department : ").append(dept).append("\n");
        summary.append("  Semester   : ").append(semester).append("\n");
        summary.append("  Section    : ").append(section.equalsIgnoreCase("All")
                ? "ALL sections (generate entries for each separately)" : section).append("\n");
        summary.append("  Courses    : ").append(courses.size()).append("\n");
        summary.append("  Faculty    : ").append(faculty.size()).append("\n");
        summary.append("  Rooms      : ").append(rooms.size()).append("\n");
        if (!existingTimetable.isEmpty()) {
            summary.append("  Existing scheduled entries (other semesters): ").append(existingTimetable.size())
                   .append(" — MUST NOT clash with these!\n");
        }
        summary.append("\n");

        summary.append("Courses to schedule (check room capacity >= studentsCount):\n");
        for (Map<String, Object> c : courses) {
            summary.append("  - ").append(c.get("id")).append(" | ").append(c.get("name"))
                   .append(" | Credits=").append(c.get("credits"))
                   .append(" | Type=").append(c.get("type"))
                   .append(" | Students=").append(c.getOrDefault("studentsCount", "N/A"))
                   .append(" | Sections=").append(c.get("sections")).append("\n");
        }

        summary.append("\nFaculty and their max daily hours:\n");
        for (Map<String, Object> f : faculty) {
            summary.append("  - ").append(f.get("id")).append(" | ").append(f.get("name"))
                   .append(" | MaxPerDay=").append(f.getOrDefault("maxHoursPerDay", 5))
                   .append(" | Courses=").append(f.get("courses")).append("\n");
        }

        summary.append("\nRooms (use correct type, respect capacity):\n");
        for (Map<String, Object> r : rooms) {
            summary.append("  - ").append(r.get("id")).append(" | ").append(r.get("name"))
                   .append(" | Type=").append(r.get("type"))
                   .append(" | Capacity=").append(r.getOrDefault("capacity", "?")).append("\n");
        }

        summary.append("\nRULES REMINDER: No 12:00-1:00 classes. No cross-semester clashes. Room capacity must fit students. Return JSON only.");
        payload.put("taskSummary", summary.toString());

        return JsonUtil.toJson(payload);
    }

    /**
     * Build a repair prompt when our validator finds violations.
     * Includes cross-semester context and specific violation details.
     */
    public static String buildRepairPrompt(
            String originalUserPrompt,
            String generatedTimetableJson,
            List<Map<String, Object>> hardConflicts,
            List<Map<String, Object>> softViolations
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your previous timetable had CONSTRAINT VIOLATIONS. Fix ALL of them.\n\n");

        if (!hardConflicts.isEmpty()) {
            sb.append("=== HARD VIOLATIONS (ALL MUST BE FIXED — 0 tolerance) ===\n");
            for (var c : hardConflicts) {
                sb.append("  [").append(c.get("type")).append("] ")
                  .append(c.get("desc"))
                  .append(" — Day: ").append(c.get("day"))
                  .append(", Slot: ").append(c.get("slot")).append("\n");
            }
            sb.append("\n");
        }

        if (!softViolations.isEmpty()) {
            sb.append("=== SOFT VIOLATIONS (fix if possible) ===\n");
            for (var v : softViolations) {
                sb.append("  [").append(v.get("type")).append("] ").append(v.get("desc")).append("\n");
            }
            sb.append("\n");
        }

        sb.append("HOW TO FIX:\n");
        sb.append("  - For Faculty Clash / Cross-Semester Faculty Clash: assign a DIFFERENT time slot\n");
        sb.append("  - For Room Clash / Cross-Semester Room Clash: assign a DIFFERENT room that is free\n");
        sb.append("  - For Room Capacity Exceeded: move to a room with capacity >= student count\n");
        sb.append("  - For Lunch Break Violation: move class OUT of 12:00-1:00 slot\n");
        sb.append("  - For Lab Room Mismatch: use a Lab-type room for lab courses\n\n");

        sb.append("=== YOUR PREVIOUS TIMETABLE (fix the violations above) ===\n");
        sb.append(generatedTimetableJson).append("\n\n");

        sb.append("Return a corrected JSON with 0 hard violations. Include any 'staffingAlerts' if a faculty is overloaded.\n");
        sb.append("RETURN JSON ONLY. No explanations.\n\n");
        sb.append("=== ORIGINAL TASK DATA (rooms, faculty, constraints, existingTimetable) ===\n");
        sb.append(originalUserPrompt);

        return sb.toString();
    }
}
