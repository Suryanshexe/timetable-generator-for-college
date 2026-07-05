import java.util.*;

public class ValidationEngine {

    public static class ValidationResult {
        public List<Map<String, Object>> conflicts = new ArrayList<>();
        public List<Map<String, Object>> softViolations = new ArrayList<>();
        public int hardCount = 0;
        public int softCount = 0;
        public int overallScore = 100;
    }

    private static final List<ConstraintValidator> validators = List.of(
        new FacultyValidator(),
        new RoomValidator(),
        new SectionValidator(),
        new LabValidator(),
        new WorkloadValidator()
    );

    public static ValidationResult validate(
            List<Map<String, Object>> timetable,
            List<Map<String, Object>> coursesList,
            List<Map<String, Object>> facultyList,
            List<Map<String, Object>> roomsList
    ) {
        ValidationResult result = new ValidationResult();

        // 1. Validate basic references first
        Map<String, Map<String, Object>> courseMap = new HashMap<>();
        for (Map<String, Object> c : coursesList) {
            courseMap.put(c.get("id").toString(), c);
        }

        Map<String, Map<String, Object>> facultyMap = new HashMap<>();
        for (Map<String, Object> f : facultyList) {
            facultyMap.put(f.get("id").toString(), f);
        }

        Map<String, Map<String, Object>> roomMap = new HashMap<>();
        for (Map<String, Object> r : roomsList) {
            roomMap.put(r.get("id").toString(), r);
        }

        for (Map<String, Object> entry : timetable) {
            String courseId = (String) entry.get("course");
            String facultyId = (String) entry.get("faculty");
            String roomId = (String) entry.get("room");
            String day = (String) entry.get("day");
            String slot = (String) entry.get("slot");

            if (courseId == null || facultyId == null || roomId == null ||
                !courseMap.containsKey(courseId) || !facultyMap.containsKey(facultyId) || !roomMap.containsKey(roomId)) {
                
                List<String> affected = new ArrayList<>();
                if (courseId != null) affected.add(courseId);
                
                addConflict(result, "Data Reference Error", "high",
                    "Invalid Course (" + courseId + "), Faculty (" + facultyId + "), or Room (" + roomId + ") reference",
                    affected, day, slot);
            }
        }

        // 2. Delegate to specialized validators
        for (ConstraintValidator validator : validators) {
            validator.validate(timetable, coursesList, facultyList, roomsList, result);
        }

        // 3. Late evening classes (4:00-5:00 PM) - Soft Constraint
        for (Map<String, Object> entry : timetable) {
            String slot = (String) entry.get("slot");
            if ("4:00-5:00".equals(slot)) {
                String courseId = (String) entry.get("course");
                addSoftViolation(result, "Late Evening Class",
                    "Class " + courseId + " is scheduled in the late evening slot (4:00-5:00)");
            }
        }

        // Calculate score
        result.hardCount = result.conflicts.size();
        result.softCount = result.softViolations.size();
        
        int penalty = (result.hardCount * 12) + (result.softCount * 2);
        result.overallScore = Math.max(0, 100 - penalty);

        return result;
    }

    public static void addConflict(ValidationResult result, String type, String severity, String desc, List<String> affected, String day, String slot) {
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("id", UUID.randomUUID().toString());
        conflict.put("type", type);
        conflict.put("severity", severity);
        conflict.put("desc", desc);
        conflict.put("affectedCourses", affected);
        conflict.put("day", day);
        conflict.put("slot", slot);
        conflict.put("status", "open");
        result.conflicts.add(conflict);
    }

    public static void addSoftViolation(ValidationResult result, String type, String desc) {
        Map<String, Object> violation = new LinkedHashMap<>();
        violation.put("type", type);
        violation.put("desc", desc);
        result.softViolations.add(violation);
    }
}

