import java.util.*;

/**
 * Intelligent conflict resolver for the timetable.
 *
 * Strategy: instead of rebuilding from scratch, this resolver performs
 * TARGETED MOVES only on conflicting entries:
 *  1. Build a live occupancy map of (faculty, room, section) × (day, slot).
 *  2. For each hard conflict, find the "offending" entry and move it to a
 *     different free (day, slot, room) triple — same course, same teacher,
 *     same section.
 *  3. For room capacity conflicts: keep same slot, find a bigger room.
 *  4. For lunch-break violations: move to any free non-lunch slot.
 *  5. Repeat until 0 hard violations or no further improvement.
 *
 * Nothing is ever deleted. The output contains exactly the same set of
 * course-section-faculty triples as the input, just rescheduled.
 */
public class ConflictResolver {

    public static class ResolveResult {
        public List<Map<String, Object>> timetable;
        public List<Map<String, Object>> existingOther;
        public ValidationEngine.ValidationResult finalValidation;
        public List<String> moveLog = new ArrayList<>();   // human-readable log of what was moved
        public int movesApplied    = 0;
        public int roundsRun       = 0;
    }

    private static final List<String> DAYS  = List.of(
            "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday");
    private static final List<String> SLOTS = List.of(
            "8:00-9:00","9:00-10:00","10:00-11:00","11:00-12:00",
            "1:00-2:00","2:00-3:00","3:00-4:00","4:00-5:00");

    /**
     * Main entry point.
     *
     * @param targetEntries   entries for the target semester/dept to fix
     * @param existingOther   all entries from other semesters (must not clash with)
     * @param courses         full course registry
     * @param faculty         full faculty registry
     * @param rooms           full room registry
     */
    public static ResolveResult resolve(
            List<Map<String, Object>> targetEntries,
            List<Map<String, Object>> existingOther,
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms
    ) {
        ResolveResult res = new ResolveResult();
        res.existingOther = existingOther != null ? existingOther : List.of();

        // Deep-copy so we don't mutate the caller's list
        List<Map<String, Object>> working = deepCopy(targetEntries);

        // Build lookup maps
        Map<String, Map<String, Object>> roomMap   = buildMap(rooms, "id");
        Map<String, Map<String, Object>> courseMap = buildMap(courses, "id");

        // Build room capacity index
        Map<String, Integer> roomCap = new HashMap<>();
        for (Map<String, Object> r : rooms) {
            if (r.get("id") != null && r.get("capacity") != null)
                roomCap.put(r.get("id").toString(), ((Number) r.get("capacity")).intValue());
        }

        // Build course student-count index
        Map<String, Integer> courseStudents = new HashMap<>();
        for (Map<String, Object> c : courses) {
            if (c.get("id") == null) continue;
            int s = 50;
            if (c.containsKey("studentsCount")) s = ((Number) c.get("studentsCount")).intValue();
            else if (c.containsKey("capacity"))  s = ((Number) c.get("capacity")).intValue();
            courseStudents.put(c.get("id").toString(), s);
        }

        // Filter rooms by type for quick lookup
        List<String> lectureRooms = new ArrayList<>();
        List<String> labRooms     = new ArrayList<>();
        List<String> allRooms     = new ArrayList<>();
        for (Map<String, Object> r : rooms) {
            if (r.get("id") == null) continue;
            String rid  = r.get("id").toString();
            String type = r.get("type") != null ? r.get("type").toString() : "Lecture";
            allRooms.add(rid);
            if ("Lab".equalsIgnoreCase(type)) labRooms.add(rid);
            else                               lectureRooms.add(rid);
        }

        int MAX_ROUNDS = 8;
        int prevHard   = Integer.MAX_VALUE;

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            res.roundsRun = round;

            // Validate current state cross-semester
            ValidationEngine.ValidationResult val =
                    ValidationEngine.validateFull(working, res.existingOther, courses, faculty, rooms);

            System.out.printf("[ConflictResolver] Round %d: %d hard, %d soft%n",
                    round, val.hardCount, val.softCount);

            if (val.hardCount == 0) {
                res.finalValidation = val;
                break;
            }
            if (val.hardCount >= prevHard) {
                // No improvement — stop to avoid infinite loop
                res.moveLog.add("⚠ No further improvement possible after round " + (round - 1) +
                        " (" + val.hardCount + " hard violations remain).");
                res.finalValidation = val;
                break;
            }
            prevHard = val.hardCount;

            // Build live occupancy map from working + existingOther
            Set<String> occFac  = new HashSet<>();
            Set<String> occRoom = new HashSet<>();
            Set<String> occSec  = new HashSet<>();

            // Pre-seed from other semesters
            for (Map<String, Object> e : res.existingOther) {
                String d = ValidationEngine.safeStr(e, "day");
                String sl= ValidationEngine.safeStr(e, "slot");
                String f = ValidationEngine.safeStr(e, "faculty");
                String r = ValidationEngine.safeStr(e, "room");
                if (d == null || sl == null) continue;
                if (f != null) occFac.add(f + "@" + d + "@" + sl);
                if (r != null) occRoom.add(r + "@" + d + "@" + sl);
            }
            // Seed from working (excluding entries we will move this round)
            for (Map<String, Object> e : working) {
                String d   = ValidationEngine.safeStr(e, "day");
                String sl  = ValidationEngine.safeStr(e, "slot");
                String fac = ValidationEngine.safeStr(e, "faculty");
                String rm  = ValidationEngine.safeStr(e, "room");
                String sec = ValidationEngine.safeStr(e, "section");
                String sem = e.get("semester") != null ? e.get("semester").toString() : "";
                if (d == null || sl == null) continue;
                if (fac != null) occFac.add(fac + "@" + d + "@" + sl);
                if (rm  != null) occRoom.add(rm + "@" + d + "@" + sl);
                if (sec != null) occSec.add(sem + "-" + sec + "@" + d + "@" + sl);
            }

            // Collect entries that appear in conflicts (by matching day+slot+course+section)
            Set<String> conflictKeys = new HashSet<>();
            for (Map<String, Object> c : val.conflicts) {
                String cday  = c.get("day") != null  ? c.get("day").toString()  : "";
                String cslot = c.get("slot") != null ? c.get("slot").toString() : "";
                List<?> affected = c.get("affectedCourses") instanceof List<?> l ? l : List.of();
                for (Object a : affected) conflictKeys.add(a.toString() + "@" + cday + "@" + cslot);
            }

            int movedThisRound = 0;

            for (int i = 0; i < working.size(); i++) {
                Map<String, Object> entry = working.get(i);
                String courseId = ValidationEngine.safeStr(entry, "course");
                String day      = ValidationEngine.safeStr(entry, "day");
                String slot     = ValidationEngine.safeStr(entry, "slot");
                String facId    = ValidationEngine.safeStr(entry, "faculty");
                String roomId   = ValidationEngine.safeStr(entry, "room");
                String section  = ValidationEngine.safeStr(entry, "section");
                String semStr   = entry.get("semester") != null ? entry.get("semester").toString() : "";

                // Is this entry implicated in a conflict?
                String entryKey = courseId + "@" + day + "@" + slot;
                boolean isConflicted = conflictKeys.contains(entryKey);

                // Also check: is this entry directly violating a constraint (lunch, capacity)?
                boolean isLunch    = "12:00-1:00".equals(slot);
                boolean isCapacity = isCapacityViolation(courseId, roomId, courseStudents, roomCap);

                if (!isConflicted && !isLunch && !isCapacity) continue;

                // ── Remove from occupancy ──────────────────────────────────────
                if (facId  != null) occFac.remove(facId  + "@" + day + "@" + slot);
                if (roomId != null) occRoom.remove(roomId + "@" + day + "@" + slot);
                if (section != null) occSec.remove(semStr + "-" + section + "@" + day + "@" + slot);

                // ── Find the course type to know which rooms are acceptable ────
                Map<String, Object> course = courseMap.get(courseId);
                String courseType = course != null && course.get("type") != null
                        ? course.get("type").toString() : "Theory";
                boolean isLab = "Lab".equalsIgnoreCase(courseType);
                List<String> candidateRooms = isLab ? labRooms : lectureRooms;
                int needed = courseStudents.getOrDefault(courseId, 50);

                // ── For capacity conflict: try same day+slot, different room ──
                if (isCapacity && !isLunch && !hasClash(facId, null, section, semStr, day, slot, occFac, occRoom, occSec)) {
                    String biggerRoom = findRoom(candidateRooms, roomCap, needed,
                            day, slot, occRoom, roomId);
                    if (biggerRoom != null) {
                        String oldRoom = roomId;
                        applyMove(working, i, day, slot, biggerRoom,
                                occFac, occRoom, occSec, facId, section, semStr);
                        res.moveLog.add("📦 Moved " + courseId + " (Sec " + section + ") " +
                                day + " " + slot + ": room " + oldRoom + " → " + biggerRoom +
                                " (capacity fix: room now fits " + needed + " students)");
                        res.movesApplied++;
                        movedThisRound++;
                        continue;
                    }
                }

                // ── Full reschedule: find new (day, slot, room) ───────────────
                boolean moved = false;
                outer:
                for (String newDay : DAYS) {
                    for (String newSlot : SLOTS) {
                        if ("12:00-1:00".equals(newSlot)) continue;
                        // Skip if same position (we already know it's conflicted there)
                        if (newDay.equals(day) && newSlot.equals(slot)) continue;
                        // Check faculty + section free
                        if (hasClash(facId, null, section, semStr, newDay, newSlot, occFac, occRoom, occSec))
                            continue;
                        // Find a room
                        String newRoom = findRoom(candidateRooms, roomCap, needed,
                                newDay, newSlot, occRoom, null);
                        if (newRoom == null) continue;

                        String oldDay  = day;
                        String oldSlot = slot;
                        applyMove(working, i, newDay, newSlot, newRoom,
                                occFac, occRoom, occSec, facId, section, semStr);
                        res.moveLog.add("🔀 Rescheduled " + courseId + " (Sec " + section + ") " +
                                oldDay + " " + oldSlot + " → " + newDay + " " + newSlot +
                                " in " + newRoom);
                        res.movesApplied++;
                        movedThisRound++;
                        moved = true;
                        break outer;
                    }
                }

                if (!moved) {
                    // Re-add to occupancy since we couldn't move it
                    if (facId  != null) occFac.add(facId  + "@" + day + "@" + slot);
                    if (roomId != null) occRoom.add(roomId + "@" + day + "@" + slot);
                    if (section != null) occSec.add(semStr + "-" + section + "@" + day + "@" + slot);
                    res.moveLog.add("⚠ Could not reschedule " + courseId + " (Sec " + section +
                            ") from " + day + " " + slot + " — no free slot found.");
                }
            }

            if (movedThisRound == 0) {
                res.moveLog.add("⚠ No moves possible in round " + round + ", stopping.");
                break;
            }
        }

        // Final validation
        if (res.finalValidation == null) {
            res.finalValidation = ValidationEngine.validateFull(
                    working, res.existingOther, courses, faculty, rooms);
        }
        res.timetable = working;

        System.out.printf("[ConflictResolver] DONE: %d moves across %d rounds → %d hard, %d soft%n",
                res.movesApplied, res.roundsRun,
                res.finalValidation.hardCount, res.finalValidation.softCount);
        return res;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private static boolean isCapacityViolation(String courseId, String roomId,
            Map<String, Integer> courseStudents, Map<String, Integer> roomCap) {
        if (courseId == null || roomId == null) return false;
        int students = courseStudents.getOrDefault(courseId, 0);
        int cap      = roomCap.getOrDefault(roomId, Integer.MAX_VALUE);
        return students > cap && cap > 0;
    }

    private static boolean hasClash(String facId, String roomId, String section, String semStr,
            String day, String slot,
            Set<String> occFac, Set<String> occRoom, Set<String> occSec) {
        if (facId  != null && occFac.contains(facId + "@" + day + "@" + slot))   return true;
        if (roomId != null && occRoom.contains(roomId + "@" + day + "@" + slot)) return true;
        if (section != null && occSec.contains(semStr + "-" + section + "@" + day + "@" + slot)) return true;
        return false;
    }

    /** Find the first room that is free at day+slot, has enough capacity, and is not excluded. */
    private static String findRoom(List<String> candidates, Map<String, Integer> roomCap,
            int needed, String day, String slot, Set<String> occRoom, String excludeRoomId) {
        for (String rid : candidates) {
            if (rid.equals(excludeRoomId)) continue;
            if (occRoom.contains(rid + "@" + day + "@" + slot)) continue;
            int cap = roomCap.getOrDefault(rid, Integer.MAX_VALUE);
            if (cap >= needed) return rid;
        }
        return null;
    }

    /** Apply a move: update working list entry i and the occupancy sets. */
    private static void applyMove(List<Map<String, Object>> working, int i,
            String newDay, String newSlot, String newRoom,
            Set<String> occFac, Set<String> occRoom, Set<String> occSec,
            String facId, String section, String semStr) {
        Map<String, Object> entry = working.get(i);
        Map<String, Object> updated = new LinkedHashMap<>(entry);
        updated.put("day",  newDay);
        updated.put("slot", newSlot);
        updated.put("room", newRoom);
        working.set(i, updated);

        // Mark new slot as occupied
        if (facId   != null) occFac.add(facId + "@" + newDay + "@" + newSlot);
        occRoom.add(newRoom + "@" + newDay + "@" + newSlot);
        if (section != null) occSec.add(semStr + "-" + section + "@" + newDay + "@" + newSlot);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> deepCopy(List<Map<String, Object>> src) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (src == null) return copy;
        for (Map<String, Object> e : src) {
            copy.add(new LinkedHashMap<>(e));
        }
        return copy;
    }

    private static Map<String, Map<String, Object>> buildMap(List<Map<String, Object>> list, String key) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> item : list) {
            if (item != null && item.get(key) != null)
                map.put(item.get(key).toString(), item);
        }
        return map;
    }
}
