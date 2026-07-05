import java.util.*;

public class LabValidator implements ConstraintValidator {

    private static final List<String> SLOT_ORDER = List.of(
        "8:00-9:00", "9:00-10:00", "10:00-11:00", "11:00-12:00", 
        "12:00-1:00", "2:00-3:00", "3:00-4:00", "4:00-5:00"
    );

    @Override
    public void validate(
        List<Map<String, Object>> timetable,
        List<Map<String, Object>> courses,
        List<Map<String, Object>> faculty,
        List<Map<String, Object>> rooms,
        ValidationEngine.ValidationResult result
    ) {
        // Index courses and rooms
        Map<String, Map<String, Object>> courseMap = new HashMap<>();
        for (Map<String, Object> c : courses) {
            courseMap.put(c.get("id").toString(), c);
        }

        Map<String, Map<String, Object>> roomMap = new HashMap<>();
        for (Map<String, Object> r : rooms) {
            roomMap.put(r.get("id").toString(), r);
        }

        // Group labs by day, course, section to check consecutive slots
        Map<String, List<Integer>> labBlocks = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            String day = (String) entry.get("day");
            String slot = (String) entry.get("slot");
            String courseId = (String) entry.get("course");
            String roomId = (String) entry.get("room");

            Map<String, Object> course = courseMap.get(courseId);
            Map<String, Object> room = roomMap.get(roomId);

            if (course == null || room == null) {
                continue;
            }

            String courseType = (String) course.get("type");
            String roomType = (String) room.get("type");

            // 1. Lab Room Check
            if ("Lab".equalsIgnoreCase(courseType) && !"Lab".equalsIgnoreCase(roomType)) {
                ValidationEngine.addConflict(result, "Lab Clash", "high",
                    "Lab session for " + courseId + " is scheduled in non-lab room " + room.get("name"),
                    List.of(courseId), day, slot);
            }

            // Record lab slots for consecutive check
            if ("Lab".equalsIgnoreCase(courseType)) {
                if (slot == null) {
                    continue;
                }
                Object sectObj = entry.get("section");
                String section = sectObj != null ? sectObj.toString() : "A";
                String key = courseId + "@" + section + "@" + day;
                int slotIdx = SLOT_ORDER.indexOf(slot);
                if (slotIdx >= 0) {
                    labBlocks.computeIfAbsent(key, k -> new ArrayList<>()).add(slotIdx);
                }
            }
        }

        // 2. Lab Session consecutive check
        for (Map.Entry<String, List<Integer>> entry : labBlocks.entrySet()) {
            String[] parts = entry.getKey().split("@");
            String courseId = parts[0];
            String sec = parts[1];
            String day = parts[2];
            List<Integer> indices = entry.getValue();
            Collections.sort(indices);

            boolean hasConsecutive = false;
            for (int i = 0; i < indices.size() - 1; i++) {
                if (indices.get(i + 1) - indices.get(i) == 1) {
                    hasConsecutive = true;
                    break;
                }
            }
            if (!hasConsecutive && !indices.isEmpty()) {
                ValidationEngine.addConflict(result, "Lab Not Contiguous", "high",
                    "Lab session for " + courseId + " Section " + sec + " on " + day + " is not in consecutive slots",
                    List.of(courseId), day, "N/A");
            }
        }
    }
}
