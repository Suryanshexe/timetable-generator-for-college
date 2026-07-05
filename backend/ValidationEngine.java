import java.util.*;

/**
 * Central validation engine for timetable constraint checking.
 * Validates ACROSS the FULL timetable (all semesters/depts combined),
 * so cross-semester room and faculty clashes are always caught.
 *
 * All validators are null-safe and handle missing references gracefully.
 */
public class ValidationEngine {

    public static class ValidationResult {
        public List<Map<String, Object>> conflicts      = new ArrayList<>();
        public List<Map<String, Object>> softViolations = new ArrayList<>();
        public List<Map<String, Object>> staffingAlerts = new ArrayList<>(); // NEW: recruit-staff suggestions
        public int hardCount    = 0;
        public int softCount    = 0;
        public int overallScore = 100;
    }

    private static final List<ConstraintValidator> validators = List.of(
        new FacultyValidator(),
        new RoomValidator(),
        new SectionValidator(),
        new LabValidator(),
        new WorkloadValidator()
    );

    /**
     * Validate only the new entries in isolation.
     * NOTE: Always prefer validateFull() when you have the existing timetable available.
     */
    public static ValidationResult validate(
            List<Map<String, Object>> newEntries,
            List<Map<String, Object>> coursesList,
            List<Map<String, Object>> facultyList,
            List<Map<String, Object>> roomsList
    ) {
        return validateFull(newEntries, List.of(), coursesList, facultyList, roomsList);
    }

    /**
     * Full cross-timetable validation.
     * @param newEntries        The new timetable entries being validated (target semester/dept)
     * @param existingEntries   All other existing timetable entries (other semesters/depts)
     * @param coursesList       All courses in the registry
     * @param facultyList       All faculty in the registry
     * @param roomsList         All rooms in the registry
     */
    public static ValidationResult validateFull(
            List<Map<String, Object>> newEntries,
            List<Map<String, Object>> existingEntries,
            List<Map<String, Object>> coursesList,
            List<Map<String, Object>> facultyList,
            List<Map<String, Object>> roomsList
    ) {
        ValidationResult result = new ValidationResult();

        if (newEntries == null) newEntries = new ArrayList<>();
        if (existingEntries == null) existingEntries = new ArrayList<>();

        if (newEntries.isEmpty()) return result;

        // ── Build lookup maps (null-safe) ──────────────────────────────────────
        Map<String, Map<String, Object>> courseMap = new HashMap<>();
        for (Map<String, Object> c : coursesList) {
            if (c != null && c.get("id") != null) courseMap.put(c.get("id").toString(), c);
        }
        Map<String, Map<String, Object>> facultyMap = new HashMap<>();
        for (Map<String, Object> f : facultyList) {
            if (f != null && f.get("id") != null) facultyMap.put(f.get("id").toString(), f);
        }
        Map<String, Map<String, Object>> roomMap = new HashMap<>();
        for (Map<String, Object> r : roomsList) {
            if (r != null && r.get("id") != null) roomMap.put(r.get("id").toString(), r);
        }

        // ── Combine full schedule for cross-check ─────────────────────────────
        // existingEntries = already committed timetable (other semesters/depts)
        // newEntries = what we are currently generating/validating

        // Pre-index existing slots (from ALL other timetable entries)
        // Key: facultyId@day@slot, roomId@day@slot
        Set<String> existingFacSlots  = new HashSet<>();
        Set<String> existingRoomSlots = new HashSet<>();
        Map<String, Map<String, Object>> existingRoomSlotMap = new HashMap<>();

        for (Map<String, Object> entry : existingEntries) {
            if (entry == null) continue;
            String fac  = safeStr(entry, "faculty");
            String room = safeStr(entry, "room");
            String day  = safeStr(entry, "day");
            String slot = safeStr(entry, "slot");
            if (day == null || slot == null) continue;
            if (fac  != null) existingFacSlots.add(fac  + "@" + day + "@" + slot);
            if (room != null) {
                String key = room + "@" + day + "@" + slot;
                existingRoomSlots.add(key);
                existingRoomSlotMap.put(key, entry);
            }
        }

        // ── 1. Data Reference Validation ──────────────────────────────────────
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            String courseId  = safeStr(entry, "course");
            String facultyId = safeStr(entry, "faculty");
            String roomId    = safeStr(entry, "room");
            String day       = safeStr(entry, "day");
            String slot      = safeStr(entry, "slot");

            List<String> badRefs = new ArrayList<>();
            if (courseId == null || !courseMap.containsKey(courseId))   badRefs.add("course=" + courseId);
            if (facultyId == null || !facultyMap.containsKey(facultyId)) badRefs.add("faculty=" + facultyId);
            if (roomId == null || !roomMap.containsKey(roomId))          badRefs.add("room=" + roomId);

            if (!badRefs.isEmpty()) {
                addConflict(result, "Data Reference Error", "high",
                    "Invalid ID references — " + String.join(", ", badRefs),
                    List.of(courseId != null ? courseId : "unknown"), day, slot);
            }
        }

        // ── 2. Lunch Break Hard Constraint ────────────────────────────────────
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            String slot = safeStr(entry, "slot");
            if ("12:00-1:00".equals(slot)) {
                String courseId = safeStr(entry, "course");
                String section  = safeStr(entry, "section");
                addConflict(result, "Lunch Break Violation", "high",
                    "Class " + courseId + " (Sec " + section + ") scheduled during reserved lunch break (12:00-1:00)",
                    List.of(courseId != null ? courseId : "unknown"),
                    safeStr(entry, "day"), slot);
            }
        }

        // ── 3. Cross-Semester Faculty Clash ───────────────────────────────────
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            String facId    = safeStr(entry, "faculty");
            String day      = safeStr(entry, "day");
            String slot     = safeStr(entry, "slot");
            String courseId = safeStr(entry, "course");
            if (facId == null || day == null || slot == null) continue;

            String key = facId + "@" + day + "@" + slot;
            if (existingFacSlots.contains(key)) {
                Map<String, Object> fac = facultyMap.get(facId);
                String facName = (fac != null && fac.get("name") != null) ? fac.get("name").toString() : facId;
                addConflict(result, "Cross-Semester Faculty Clash", "high",
                    facName + " is already teaching another class (different semester/dept) on " + day + " at " + slot +
                    " — cannot also teach " + courseId,
                    List.of(courseId != null ? courseId : "unknown"), day, slot);
            }
        }

        // ── 4. Cross-Semester Room Clash ──────────────────────────────────────
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            String roomId   = safeStr(entry, "room");
            String day      = safeStr(entry, "day");
            String slot     = safeStr(entry, "slot");
            String courseId = safeStr(entry, "course");
            if (roomId == null || day == null || slot == null) continue;

            String key = roomId + "@" + day + "@" + slot;
            if (existingRoomSlots.contains(key)) {
                Map<String, Object> room = roomMap.get(roomId);
                String roomName = (room != null && room.get("name") != null) ? room.get("name").toString() : roomId;
                Map<String, Object> conflictEntry = existingRoomSlotMap.get(key);
                String otherCourse = conflictEntry != null ? safeStr(conflictEntry, "course") : "another class";
                String otherSem = conflictEntry != null ? safeStr(conflictEntry, "semester") : "another semester";
                addConflict(result, "Cross-Semester Room Clash", "high",
                    roomName + " is already occupied by " + otherCourse + " (Sem " + otherSem + ") on " + day +
                    " at " + slot + " — cannot also host " + courseId,
                    List.of(courseId != null ? courseId : "unknown"), day, slot);
            }
        }

        // ── 5. Room Capacity Check ────────────────────────────────────────────
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            String roomId   = safeStr(entry, "room");
            String courseId = safeStr(entry, "course");
            String day      = safeStr(entry, "day");
            String slot     = safeStr(entry, "slot");
            if (roomId == null || courseId == null) continue;

            Map<String, Object> room   = roomMap.get(roomId);
            Map<String, Object> course = courseMap.get(courseId);
            if (room == null || course == null) continue;

            int roomCap = room.containsKey("capacity") ? ((Number) room.get("capacity")).intValue() : 0;
            int students = 50; // default assumption
            if (course.containsKey("studentsCount")) {
                students = ((Number) course.get("studentsCount")).intValue();
            } else if (course.containsKey("capacity")) {
                students = ((Number) course.get("capacity")).intValue();
            }

            if (roomCap > 0 && students > roomCap) {
                addConflict(result, "Room Capacity Exceeded", "high",
                    "Room " + room.get("name") + " (capacity=" + roomCap + ") cannot fit " + students +
                    " students of " + courseId + ". Move to a larger room.",
                    List.of(courseId), day, slot);
            }
        }

        // ── 6. Specialized Validators (within-timetable checks) ───────────────
        for (ConstraintValidator validator : validators) {
            try {
                validator.validate(newEntries, coursesList, facultyList, roomsList, result);
            } catch (Exception e) {
                System.err.println("[ValidationEngine] Validator " + validator.getClass().getSimpleName() +
                        " threw exception: " + e.getMessage());
            }
        }

        // ── 7. Soft: Late Evening Classes ─────────────────────────────────────
        Set<String> lateReported = new HashSet<>();
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            if ("4:00-5:00".equals(safeStr(entry, "slot"))) {
                String k = safeStr(entry, "course") + "@" + safeStr(entry, "section") + "@" + safeStr(entry, "day");
                if (lateReported.add(k)) {
                    addSoftViolation(result, "Late Evening Class",
                        "Class " + safeStr(entry, "course") + " (Sec " + safeStr(entry, "section") +
                        ") is in the late slot 4:00-5:00 on " + safeStr(entry, "day"));
                }
            }
        }

        // ── 8. Soft: Saturday Classes ─────────────────────────────────────────
        Map<String, Integer> satCount = new HashMap<>();
        for (Map<String, Object> entry : newEntries) {
            if (entry == null) continue;
            if ("Saturday".equals(safeStr(entry, "day"))) {
                String c = safeStr(entry, "course");
                if (c != null) satCount.merge(c, 1, Integer::sum);
            }
        }
        if (!satCount.isEmpty()) {
            addSoftViolation(result, "Saturday Classes",
                satCount.size() + " course(s) have Saturday classes: " + satCount.keySet());
        }

        // ── 9. Faculty Overload → Staffing Alerts ────────────────────────────
        // Combine new + existing to get total weekly load per faculty
        List<Map<String, Object>> allEntries = new ArrayList<>(newEntries);
        allEntries.addAll(existingEntries);

        Map<String, Integer> facTotalHours = new HashMap<>();
        for (Map<String, Object> entry : allEntries) {
            if (entry == null) continue;
            String facId = safeStr(entry, "faculty");
            if (facId != null) facTotalHours.merge(facId, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> e : facTotalHours.entrySet()) {
            String facId    = e.getKey();
            int totalHours  = e.getValue();
            Map<String, Object> fac = facultyMap.get(facId);
            if (fac == null) continue;

            // Max weekly hours = maxHoursPerDay * 5 days; default 5*5=25 but flag if > 20 practical limit
            int maxPerDay   = fac.containsKey("maxHoursPerDay") ? ((Number) fac.get("maxHoursPerDay")).intValue() : 5;
            int maxWeekly   = maxPerDay * 5; // Mon–Fri

            if (totalHours > maxWeekly) {
                String facName = fac.get("name") != null ? fac.get("name").toString() : facId;
                String dept    = fac.get("dept") != null ? fac.get("dept").toString() : "?";

                // Find which courses this faculty teaches
                List<String> taughtCourses = new ArrayList<>();
                for (Map<String, Object> entry : allEntries) {
                    if (facId.equals(safeStr(entry, "faculty"))) {
                        String c = safeStr(entry, "course");
                        if (c != null && !taughtCourses.contains(c)) taughtCourses.add(c);
                    }
                }

                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("type", "Staffing Alert");
                alert.put("severity", "warning");
                alert.put("facultyId", facId);
                alert.put("facultyName", facName);
                alert.put("dept", dept);
                alert.put("currentWeeklyHours", totalHours);
                alert.put("maxAllowedWeeklyHours", maxWeekly);
                alert.put("overloadHours", totalHours - maxWeekly);
                alert.put("taughtCourses", taughtCourses);
                alert.put("recommendation",
                    "⚠ RECRUIT additional teaching staff for " + dept + " department. " +
                    facName + " is overloaded by " + (totalHours - maxWeekly) + " hours/week " +
                    "(teaching " + totalHours + " hrs, max allowed " + maxWeekly + " hrs). " +
                    "Consider hiring an additional faculty for: " + taughtCourses);
                result.staffingAlerts.add(alert);
            }
        }

        // ── 10. Score Calculation ─────────────────────────────────────────────
        result.hardCount = result.conflicts.size();
        result.softCount = result.softViolations.size();
        int penalty = (result.hardCount * 12) + (result.softCount * 2);
        result.overallScore = Math.max(0, 100 - penalty);

        return result;
    }

    // ── Helper Methods ──────────────────────────────────────────────────────────

    public static void addConflict(ValidationResult result, String type, String severity,
            String desc, List<String> affected, String day, String slot) {
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("id", UUID.randomUUID().toString());
        conflict.put("type", type);
        conflict.put("severity", severity);
        conflict.put("desc", desc);
        conflict.put("affectedCourses", affected != null ? affected : List.of());
        conflict.put("day", day != null ? day : "");
        conflict.put("slot", slot != null ? slot : "");
        conflict.put("status", "open");
        result.conflicts.add(conflict);
    }

    public static void addSoftViolation(ValidationResult result, String type, String desc) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("type", type);
        v.put("desc", desc);
        result.softViolations.add(v);
    }

    /** Null-safe string extraction from a map */
    static String safeStr(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
