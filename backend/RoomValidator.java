import java.util.*;

public class RoomValidator implements ConstraintValidator {

    @Override
    public void validate(
        List<Map<String, Object>> timetable,
        List<Map<String, Object>> courses,
        List<Map<String, Object>> faculty,
        List<Map<String, Object>> rooms,
        ValidationEngine.ValidationResult result
    ) {
        // Index rooms and courses
        Map<String, Map<String, Object>> roomMap = new HashMap<>();
        for (Map<String, Object> r : rooms) {
            roomMap.put(r.get("id").toString(), r);
        }

        Map<String, Map<String, Object>> courseMap = new HashMap<>();
        for (Map<String, Object> c : courses) {
            courseMap.put(c.get("id").toString(), c);
        }

        // Track allocations
        // Key: roomId + "@" + day + "@" + slot
        Map<String, Map<String, Object>> roomSlots = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            String day = (String) entry.get("day");
            String slot = (String) entry.get("slot");
            String courseId = (String) entry.get("course");
            String roomId = (String) entry.get("room");
            
            if (day == null || slot == null || roomId == null) {
                continue;
            }

            Map<String, Object> room = roomMap.get(roomId);

            Map<String, Object> course = courseMap.get(courseId);

            if (room == null || course == null) {
                continue;
            }

            // 1. Room Clash
            String roomKey = roomId + "@" + day + "@" + slot;
            if (roomSlots.containsKey(roomKey)) {
                Map<String, Object> other = roomSlots.get(roomKey);
                ValidationEngine.addConflict(result, "Room Clash", "high",
                    "Room " + room.get("name") + " is double-booked for " + courseId + " and " + other.get("course"),
                    List.of(courseId, (String) other.get("course")), day, slot);
            } else {
                roomSlots.put(roomKey, entry);
            }

            // 2. Room Capacity Check
            long studentCount = 50;
            if (course.containsKey("studentsCount")) {
                studentCount = ((Number) course.get("studentsCount")).longValue();
            } else if (course.containsKey("capacity")) {
                studentCount = ((Number) course.get("capacity")).longValue();
            }

            long roomCap = room.containsKey("capacity") ? ((Number) room.get("capacity")).longValue() : 0;
            if (roomCap < studentCount) {
                ValidationEngine.addConflict(result, "Capacity Issue", "high",
                    "Room " + room.get("name") + " capacity (" + roomCap + ") is less than student count (" + studentCount + ") for " + courseId,
                    List.of(courseId), day, slot);
            }
        }
    }
}
