import java.util.*;

public class SectionValidator implements ConstraintValidator {

    @Override
    public void validate(
        List<Map<String, Object>> timetable,
        List<Map<String, Object>> courses,
        List<Map<String, Object>> faculty,
        List<Map<String, Object>> rooms,
        ValidationEngine.ValidationResult result
    ) {
        // Track allocations
        // Key: semester-section + "@" + day + "@" + slot
        Map<String, Map<String, Object>> sectionSlots = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            String day = (String) entry.get("day");
            String slot = (String) entry.get("slot");
            String courseId = (String) entry.get("course");
            
            if (day == null || slot == null || courseId == null) {
                continue;
            }

            Object sectObj = entry.get("section");

            String section = sectObj != null ? sectObj.toString() : "A";
            
            Object semObj = entry.get("semester");
            String semester = semObj != null ? semObj.toString() : "5";

            // 1. Student Section Clash
            String secKey = semester + "-" + section + "@" + day + "@" + slot;
            if (sectionSlots.containsKey(secKey)) {
                Map<String, Object> other = sectionSlots.get(secKey);
                ValidationEngine.addConflict(result, "Student Section Clash", "high",
                    "Student Section Sem " + semester + " Sec " + section + " has parallel classes (" + courseId + " and " + other.get("course") + ")",
                    List.of(courseId, (String) other.get("course")), day, slot);
            } else {
                sectionSlots.put(secKey, entry);
            }

            // 2. Lunch Break Reserved
            if ("12:00-1:00".equals(slot)) {
                ValidationEngine.addConflict(result, "Lunch Break Violation", "high",
                    "Class " + courseId + " scheduled during restricted lunch break (12:00-1:00)",
                    List.of(courseId), day, slot);
            }
        }
    }
}
