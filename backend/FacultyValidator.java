import java.util.*;

public class FacultyValidator implements ConstraintValidator {

    @Override
    public void validate(
        List<Map<String, Object>> timetable,
        List<Map<String, Object>> courses,
        List<Map<String, Object>> faculty,
        List<Map<String, Object>> rooms,
        ValidationEngine.ValidationResult result
    ) {
        // Index faculty data
        Map<String, Map<String, Object>> facultyMap = new HashMap<>();
        for (Map<String, Object> f : faculty) {
            facultyMap.put(f.get("id").toString(), f);
        }

        // Track allocations
        // Key: facultyId + "@" + day + "@" + slot
        Map<String, Map<String, Object>> facultySlots = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            String day = (String) entry.get("day");
            String slot = (String) entry.get("slot");
            String courseId = (String) entry.get("course");
            String facultyId = (String) entry.get("faculty");
            
            if (day == null || slot == null || facultyId == null) {
                continue;
            }

            Map<String, Object> fac = facultyMap.get(facultyId);

            if (fac == null) {
                continue; // Handled by reference validation or other validators
            }

            // 1. Faculty Clash
            String facKey = facultyId + "@" + day + "@" + slot;
            if (facultySlots.containsKey(facKey)) {
                Map<String, Object> other = facultySlots.get(facKey);
                ValidationEngine.addConflict(result, "Faculty Clash", "high",
                    fac.get("name") + " is scheduled for two classes simultaneously (" + courseId + " and " + other.get("course") + ")",
                    List.of(courseId, (String) other.get("course")), day, slot);
            } else {
                facultySlots.put(facKey, entry);
            }

            // 2. Unavailable Slots Check
            if (fac.containsKey("unavailableSlots")) {
                List<?> unavail = (List<?>) fac.get("unavailableSlots");
                String checkStr = day + " " + slot;
                if (unavail.contains(checkStr) || unavail.contains(slot) || unavail.contains(day)) {
                    ValidationEngine.addConflict(result, "Unavailable Faculty", "high",
                        fac.get("name") + " is unavailable during " + (unavail.contains(day) ? day : checkStr),
                        List.of(courseId), day, slot);
                }
            }

        }
    }
}
