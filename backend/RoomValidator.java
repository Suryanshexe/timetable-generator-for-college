import java.util.*;

/**
 * Validates room-level hard constraints:
 * 1. Room clash (same room used by two classes at same day+slot within the same timetable batch)
 * 2. Room type mismatch (Lab course in Lecture room, Theory in Lab room)
 *
 * NOTE: Cross-semester room clash and capacity violations are handled by ValidationEngine directly.
 */
public class RoomValidator implements ConstraintValidator {

    @Override
    public void validate(
        List<Map<String, Object>> timetable,
        List<Map<String, Object>> courses,
        List<Map<String, Object>> faculty,
        List<Map<String, Object>> rooms,
        ValidationEngine.ValidationResult result
    ) {
        // Build lookup maps (null-safe)
        Map<String, Map<String, Object>> roomMap = new HashMap<>();
        for (Map<String, Object> r : rooms) {
            if (r != null && r.get("id") != null) roomMap.put(r.get("id").toString(), r);
        }
        Map<String, Map<String, Object>> courseMap = new HashMap<>();
        for (Map<String, Object> c : courses) {
            if (c != null && c.get("id") != null) courseMap.put(c.get("id").toString(), c);
        }

        // Track room occupancy within this timetable batch
        // Key: roomId@day@slot → first entry that took it
        Map<String, Map<String, Object>> roomSlots = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            if (entry == null) continue;
            String day      = ValidationEngine.safeStr(entry, "day");
            String slot     = ValidationEngine.safeStr(entry, "slot");
            String courseId = ValidationEngine.safeStr(entry, "course");
            String roomId   = ValidationEngine.safeStr(entry, "room");

            if (day == null || slot == null || roomId == null) continue;

            Map<String, Object> room   = roomMap.get(roomId);
            Map<String, Object> course = courseMap.get(courseId);

            // ── 1. Within-batch Room Clash ──────────────────────────────────
            String roomKey = roomId + "@" + day + "@" + slot;
            if (roomSlots.containsKey(roomKey)) {
                Map<String, Object> other = roomSlots.get(roomKey);
                String roomName = (room != null && room.get("name") != null) ? room.get("name").toString() : roomId;
                addIfNotDuplicate(result, "Room Clash", "high",
                    "Room " + roomName + " is double-booked — " + courseId + " and " +
                    ValidationEngine.safeStr(other, "course") + " both at " + day + " " + slot,
                    List.of(
                        courseId != null ? courseId : "unknown",
                        ValidationEngine.safeStr(other, "course") != null ? ValidationEngine.safeStr(other, "course") : "unknown"
                    ), day, slot);
            } else {
                roomSlots.put(roomKey, entry);
            }

            // ── 2. Room Type Mismatch ─────────────────────────────────────
            if (room == null || course == null) continue;
            String roomType   = room.get("type") != null ? room.get("type").toString() : "";
            String courseType = course.get("type") != null ? course.get("type").toString() : "Theory";

            boolean isLabCourse = "Lab".equalsIgnoreCase(courseType);
            boolean isLabRoom   = "Lab".equalsIgnoreCase(roomType);

            if (isLabCourse && !isLabRoom) {
                addIfNotDuplicate(result, "Room Type Mismatch", "high",
                    "Lab course " + courseId + " is assigned to non-lab room " + room.get("name") +
                    " (type=" + roomType + "). Must use a Lab room.",
                    List.of(courseId), day, slot);
            } else if (!isLabCourse && isLabRoom) {
                addIfNotDuplicate(result, "Room Type Mismatch", "high",
                    "Theory course " + courseId + " is assigned to Lab room " + room.get("name") +
                    ". Lab rooms must be reserved for lab sessions only.",
                    List.of(courseId), day, slot);
            }
        }
    }

    private void addIfNotDuplicate(
            ValidationEngine.ValidationResult result,
            String type, String severity, String desc,
            List<String> affected, String day, String slot) {
        // Avoid duplicate messages for same room+day+slot combination
        for (Map<String, Object> c : result.conflicts) {
            if (type.equals(c.get("type")) && day.equals(c.get("day")) && slot.equals(c.get("slot"))) {
                // Check if same affected courses
                Object existingAffected = c.get("affectedCourses");
                if (existingAffected instanceof List<?> l) {
                    boolean overlap = affected.stream().anyMatch(a -> l.contains(a));
                    if (overlap) return; // already reported
                }
            }
        }
        ValidationEngine.addConflict(result, type, severity, desc, affected, day, slot);
    }
}
