import java.util.List;
import java.util.Map;

public class PromptBuilder {

    public static String buildSystemPrompt(String scheduleType) {
        String base = "You are an expert University Timetable Scheduling Engine.\n" +
                "Generate a complete weekly timetable.\n" +
                "Rules\n" +
                "Return JSON only.\n" +
                "Never explain.\n" +
                "Never invent data.\n" +
                "Use only supplied JSON.\n";

        if ("easy".equalsIgnoreCase(scheduleType)) {
            base += "Enforce Easy Schedule Constraint: Ensure there is at least one slot gap between any two classes for a given student section and for any faculty member. Do NOT schedule classes back-to-back.\n";
        } else {
            base += "Enforce Tight Schedule Constraint: Schedule classes back-to-back (one after another) with minimal gaps between classes.\n";
        }

        base += "Room Distribution Constraint: Do NOT schedule all classes sequentially in a single room. Utilize all available rooms (e.g., R101, R201) in parallel at the same time to distribute the load evenly and prevent time clashing.\n";

        return base +
                "Hard Constraints\n" +
                "No Faculty Clash\n" +
                "No Room Clash\n" +
                "No Student Clash\n" +
                "Respect Faculty Availability\n" +
                "Respect Room Capacity\n" +
                "Complete Weekly Hours\n" +
                "Labs only in Labs\n" +
                "Lunch Break Reserved\n" +
                "Consecutive Lab Sessions\n" +
                "College Timing\n" +
                "Soft Constraints\n" +
                "Minimize\n" +
                "Idle Gaps\n" +
                "Back to Back Lectures\n" +
                "Uneven Faculty Load\n" +
                "Late Evening Classes\n" +
                "Preferred Timing Violations\n" +
                "Preferred Room Violations\n" +
                "Return\n" +
                "{\n" +
                "  \"timetable\": [],\n" +
                "  \"conflicts\": [],\n" +
                "  \"suggestions\": [],\n" +
                "  \"facultyWorkload\": [],\n" +
                "  \"roomUtilization\": [],\n" +
                "  \"analytics\": {},\n" +
                "  \"score\": {\n" +
                "    \"hardViolations\":0,\n" +
                "    \"softViolations\":0,\n" +
                "    \"overallScore\":100\n" +
                "  }\n" +
                "}\n" +
                "Return JSON only.";
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
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("courses", courses);
        payload.put("faculty", faculty);
        payload.put("rooms", rooms);
        payload.put("targetDept", dept);
        payload.put("targetSemester", semester);
        payload.put("targetSection", section);
        payload.put("timeSlots", slots);
        payload.put("days", days);
        payload.put("constraints", constraints);
        if (customCommand != null && !customCommand.isBlank()) {
            payload.put("additionalInstruction", customCommand);
        }
        return JsonUtil.toJson(payload);
    }
}
