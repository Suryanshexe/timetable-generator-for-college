import java.util.*;

public class WorkloadValidator implements ConstraintValidator {

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
        // Index courses and faculty
        Map<String, Map<String, Object>> courseMap = new HashMap<>();
        for (Map<String, Object> c : courses) {
            courseMap.put(c.get("id").toString(), c);
        }

        Map<String, Map<String, Object>> facultyMap = new HashMap<>();
        for (Map<String, Object> f : faculty) {
            facultyMap.put(f.get("id").toString(), f);
        }

        // Track course hours per section
        // Key: courseId + "@" + section -> list of entries
        Map<String, List<Map<String, Object>>> courseSectionHours = new HashMap<>();

        // Track daily workloads
        // Key: facultyId + "@" + day -> count
        Map<String, Integer> facultyDailyHours = new HashMap<>();

        // Track section daily slots
        // Key: semester-section + "@" + day -> list of slot indices
        Map<String, List<Integer>> sectionDailySlots = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            String day = (String) entry.get("day");
            String slot = (String) entry.get("slot");
            if (day == null || slot == null) {
                continue;
            }
            String courseId = (String) entry.get("course");
            String facultyId = (String) entry.get("faculty");
            
            Object sectObj = entry.get("section");

            String section = sectObj != null ? sectObj.toString() : "A";

            Object semObj = entry.get("semester");
            String semester = semObj != null ? semObj.toString() : "5";

            int slotIdx = SLOT_ORDER.indexOf(slot);

            // Record weekly course-section hours
            String csKey = courseId + "@" + section;
            courseSectionHours.computeIfAbsent(csKey, k -> new ArrayList<>()).add(entry);

            // Record faculty daily hours
            String facDayKey = facultyId + "@" + day;
            facultyDailyHours.put(facDayKey, facultyDailyHours.getOrDefault(facDayKey, 0) + 1);

            // Record section daily slots
            String secDayKey = semester + "-" + section + "@" + day;
            if (slotIdx >= 0) {
                sectionDailySlots.computeIfAbsent(secDayKey, k -> new ArrayList<>()).add(slotIdx);
            }
        }

        // 1. Weekly hours completion check
        for (Map.Entry<String, Map<String, Object>> entry : courseMap.entrySet()) {
            String courseId = entry.getKey();
            Map<String, Object> course = entry.getValue();
            long credits = course.containsKey("credits") ? ((Number) course.get("credits")).longValue() : 3;

            List<?> sections = (List<?>) course.get("sections");
            if (sections == null) sections = List.of("A");

            for (Object secObj : sections) {
                String sec = secObj.toString();
                String csKey = courseId + "@" + sec;
                List<Map<String, Object>> scheduled = courseSectionHours.getOrDefault(csKey, List.of());

                if (scheduled.size() < credits) {
                    ValidationEngine.addConflict(result, "Missing Lecture Hours", "medium",
                        "Course " + courseId + " Section " + sec + " has only " + scheduled.size() + "/" + credits + " hours scheduled",
                        List.of(courseId), "Multiple", "N/A");
                } else if (scheduled.size() > credits) {
                    ValidationEngine.addConflict(result, "Duplicate Allocations", "medium",
                        "Course " + courseId + " Section " + sec + " is over-scheduled: " + scheduled.size() + "/" + credits + " hours",
                        List.of(courseId), "Multiple", "N/A");
                }
            }
        }

        // 2. Daily workload per faculty (Max 5 hours preferred)
        for (Map.Entry<String, Integer> entry : facultyDailyHours.entrySet()) {
            int hrs = entry.getValue();
            if (hrs > 5) {
                String[] parts = entry.getKey().split("@");
                String facId = parts[0];
                String day = parts[1];
                Map<String, Object> fac = facultyMap.get(facId);
                String facName = fac != null ? fac.get("name").toString() : facId;

                ValidationEngine.addSoftViolation(result, "Uneven Workload",
                    facName + " is scheduled for " + hrs + " hours on " + day + " (exceeds preferred 5 hrs limit)");
            }
        }

        // 3. Section daily limits, back-to-back lectures, idle gaps
        for (Map.Entry<String, List<Integer>> entry : sectionDailySlots.entrySet()) {
            String[] parts = entry.getKey().split("@");
            String secKey = parts[0];
            String day = parts[1];
            List<Integer> slots = entry.getValue();
            Collections.sort(slots);

            // Too many lectures
            if (slots.size() > 4) {
                ValidationEngine.addSoftViolation(result, "Too Many Lectures",
                    "Section " + secKey + " has " + slots.size() + " lectures scheduled on " + day);
            }

            // Idle gaps
            for (int i = 0; i < slots.size() - 1; i++) {
                int gap = slots.get(i + 1) - slots.get(i);
                if (gap > 2) {
                    ValidationEngine.addSoftViolation(result, "Long Idle Gap",
                        "Section " + secKey + " has a long idle gap of " + (gap - 1) + " hours on " + day);
                }
            }

            // Back-to-back lectures (exceeding 3)
            int consecutive = 1;
            for (int i = 0; i < slots.size() - 1; i++) {
                if (slots.get(i + 1) - slots.get(i) == 1) {
                    consecutive++;
                    if (consecutive > 3) {
                        ValidationEngine.addSoftViolation(result, "Back-To-Back Lectures",
                            "Section " + secKey + " has " + consecutive + " classes scheduled consecutively on " + day);
                    }
                } else {
                    consecutive = 1;
                }
            }
        }
    }
}
