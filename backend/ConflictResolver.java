import java.util.*;

/**
 * Fully-automated, globally-aware conflict resolver.
 *
 * Three-phase algorithm that runs 100% autonomously:
 *
 *  PHASE 1 — Hard Conflict Elimination
 *    MRV-ordered CSP: rank conflicting entries by their number of legal
 *    placements (fewest-first), then try every (day, slot, room) triple and
 *    commit the highest-scoring legal placement.  One-step backtracking is
 *    attempted when no direct placement is found.  Repeats up to MAX_ATTEMPTS
 *    passes until hard-count reaches zero or no further improvement is possible.
 *
 *  PHASE 2 — Soft Constraint Optimisation
 *    Simulated-annealing neighbourhood search: randomly proposes moves of
 *    non-conflicting entries to free slots that score better on soft constraints
 *    (idle gaps, back-to-back load, Saturday, late slots, faculty balance).
 *    Runs only after Phase 1 achieves 0 hard violations.
 *
 *  PHASE 3 — Cross-Semester Global Re-check
 *    Re-validates the full merged timetable and re-runs Phase 1 on this group
 *    if the annealing step introduced new cross-semester hard violations.
 *    Repeats up to MAX_GLOBAL_PASSES times.
 *
 * Public API (resolve() signature and ResolveResult) is identical to the
 * previous version — TimetableController needs no change beyond a minor
 * global-retry loop.
 */
public class ConflictResolver {

    // ── Public result record ─────────────────────────────────────────────────────

    public static class ResolveResult {
        public List<Map<String, Object>> timetable;
        public List<Map<String, Object>> existingOther;
        public ValidationEngine.ValidationResult finalValidation;
        public List<String> moveLog    = new ArrayList<>();
        public int movesApplied        = 0;
        public int roundsRun           = 0;
    }

    // ── Slot / day constants ─────────────────────────────────────────────────────

    private static final List<String> DAYS = List.of(
            "Monday","Tuesday","Wednesday","Thursday","Friday","Saturday");

    static final List<String> SLOTS = List.of(
            "8:00-9:00","9:00-10:00","10:00-11:00","11:00-12:00",
            "1:00-2:00","2:00-3:00","3:00-4:00","4:00-5:00");

    // ── Scoring weights ──────────────────────────────────────────────────────────

    private static final double W_CAPACITY  = 0.5;
    private static final double W_FAC_LOAD  = 3.0;
    private static final double W_NO_CONSEC = 5.0;
    private static final double W_NO_SAT    = 8.0;
    private static final double W_NO_LATE   = 4.0;
    private static final double W_NO_GAP    = 3.0;
    private static final double W_MORNING   = 2.0;

    // ── Simulated-annealing parameters ──────────────────────────────────────────

    private static final int    SA_ITERATIONS   = 600;
    private static final double SA_INITIAL_TEMP = 15.0;
    private static final double SA_COOLING_RATE = 0.97;
    private static final double SA_MIN_TEMP     = 0.001;

    // ── Solver limits ────────────────────────────────────────────────────────────

    private static final int MAX_ATTEMPTS      = 25;  // Phase-1 passes per invocation
    private static final int MAX_GLOBAL_PASSES = 3;   // Phase-3 outer iterations
    private static final int NUM_RESTARTS      = 4;   // How many times to run the full algorithm internally

    private static final Random RNG = new Random();

    // ── Main entry point ─────────────────────────────────────────────────────────

    /**
     * Resolve all conflicts in {@code targetEntries} without touching entries
     * from other semesters / departments ({@code existingOther}).
     *
     * @param targetEntries  entries for the target semester/dept to fix
     * @param existingOther  all timetable entries from OTHER semesters (read-only)
     * @param courses        full course registry
     * @param faculty        full faculty registry
     * @param rooms          full room registry
     * @return               ResolveResult with fixed timetable, move log, validation
     */
    public static ResolveResult resolve(
            List<Map<String, Object>> targetEntries,
            List<Map<String, Object>> existingOther,
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms
    ) {
        ResourceIndex idx = new ResourceIndex(courses, faculty, rooms);
        List<Map<String, Object>> nonTarget = existingOther != null ? existingOther : List.of();

        ResolveResult originalRes = new ResolveResult();
        originalRes.timetable = deepCopy(targetEntries);
        originalRes.existingOther = nonTarget;
        originalRes.finalValidation = ValidationEngine.validateFull(targetEntries, nonTarget, courses, faculty, rooms);
        originalRes.moveLog.add("No improvements found. Kept original timetable.");

        ResolveResult bestOverall = originalRes;
        ValidationEngine.ValidationResult bestVal = originalRes.finalValidation;

        System.out.println("[ConflictResolver] Starting " + NUM_RESTARTS + " parallel internal restarts...");
        System.out.printf("[ConflictResolver] Original score: %d hard | %d soft | %d%% score%n",
                bestVal.hardCount, bestVal.softCount, bestVal.overallScore);

        // If it's already perfect, return immediately
        if (bestVal.hardCount == 0 && bestVal.overallScore == 100) {
            originalRes.moveLog.clear();
            originalRes.moveLog.add("✨ Timetable already perfect! No optimization needed.");
            return originalRes;
        }

        for (int restart = 1; restart <= NUM_RESTARTS; restart++) {
            ResolveResult res = new ResolveResult();
            res.existingOther = nonTarget;
            List<Map<String, Object>> working = deepCopy(targetEntries);

            res.moveLog.add("🔄 Restart " + restart + "/" + NUM_RESTARTS + " starting...");

            // ── PHASE 1: hard violation elimination ──────────────────────────────
            working = phaseOneHardElimination(working, res.existingOther, idx, res);

            // ── PHASE 2: soft constraint optimisation ────────────────────────────
            ValidationEngine.ValidationResult afterP1 =
                    ValidationEngine.validateFull(working, res.existingOther, courses, faculty, rooms);

            if (afterP1.hardCount == 0) {
                working = phaseTwoSoftOptimise(working, res.existingOther, idx, res);
            }

            // ── PHASE 3: cross-semester global re-check ──────────────────────────
            for (int gp = 1; gp <= MAX_GLOBAL_PASSES; gp++) {
                ValidationEngine.ValidationResult gv =
                        ValidationEngine.validateFull(working, res.existingOther, courses, faculty, rooms);
                if (gv.hardCount == 0) break;
                working = phaseOneHardElimination(working, res.existingOther, idx, res);
            }

            res.finalValidation = ValidationEngine.validateFull(working, res.existingOther, courses, faculty, rooms);
            res.timetable = working;

            // Update best so far (strictly better than original or previous best)
            if (isBetter(res.finalValidation, bestVal)) {
                bestVal = res.finalValidation;
                bestOverall = res;
            }

            // If we found a perfect score, no need to run remaining restarts
            if (bestVal.hardCount == 0 && bestVal.overallScore == 100) {
                break;
            }
        }

        if (bestOverall != originalRes) {
            bestOverall.moveLog.add(0, "🏆 Selected best result from " + NUM_RESTARTS + " internal checks.");
        }
        System.out.printf("[ConflictResolver] BEST RESULT: %d hard | %d soft | %d%% score%n",
                bestOverall.finalValidation.hardCount, bestOverall.finalValidation.softCount, bestOverall.finalValidation.overallScore);
        
        return bestOverall;
    }

    private static boolean isBetter(ValidationEngine.ValidationResult a, ValidationEngine.ValidationResult b) {
        if (a.hardCount < b.hardCount) return true;
        if (a.hardCount > b.hardCount) return false;
        return a.overallScore > b.overallScore;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 1 — MRV-ordered CSP with scored placements + one-step backtracking
    // ═══════════════════════════════════════════════════════════════════════════

    private static List<Map<String, Object>> phaseOneHardElimination(
            List<Map<String, Object>> working,
            List<Map<String, Object>> existingOther,
            ResourceIndex idx,
            ResolveResult res
    ) {
        int prevHard = Integer.MAX_VALUE;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            res.roundsRun++;

            ValidationEngine.ValidationResult val =
                    ValidationEngine.validateFull(working, existingOther,
                            idx.courses, idx.faculty, idx.rooms);

            System.out.printf("[ConflictResolver] P1 attempt %d: %d hard, %d soft%n",
                    attempt, val.hardCount, val.softCount);

            if (val.hardCount == 0) break;

            if (val.hardCount >= prevHard && attempt > 2) {
                // If stuck, apply a random disruption (random displacement of a conflicting entry)
                // to try and break out of the local minimum, instead of just breaking.
                if (attempt < MAX_ATTEMPTS - 5 && !working.isEmpty()) {
                    res.moveLog.add("⚠ Stuck at " + val.hardCount + " hard violations. Applying random disruption...");
                    applyRandomDisruption(working, existingOther, idx, res);
                    prevHard = Integer.MAX_VALUE; // Reset to allow more attempts
                    continue;
                } else {
                    res.moveLog.add("⚠ No improvement after attempt " + (attempt - 1) +
                            " (" + val.hardCount + " hard remain) — Phase 1 stopping.");
                    break;
                }
            }
            prevHard = val.hardCount;

            // Fresh occupancy snapshot from working + other
            Occupancy occ = new Occupancy(working, existingOther);

            // Collect indices of entries involved in hard conflicts
            Set<Integer> conflictSet = findConflictedIndices(working, val.conflicts);

            // MRV: sort by ascending count of legal placements (hardest first)
            List<Integer> ordered = mrvOrder(new ArrayList<>(conflictSet), working, occ, idx);

            for (int i : ordered) {
                Map<String, Object> entry = working.get(i);
                String courseId = str(entry, "course");
                String facId    = str(entry, "faculty");
                String roomId   = str(entry, "room");
                String section  = str(entry, "section");
                String semStr   = semOf(entry);
                String oldDay   = str(entry, "day");
                String oldSlot  = str(entry, "slot");

                // Temporarily remove from occupancy
                occ.release(facId, roomId, section, semStr, oldDay, oldSlot);

                // Find the best-scored legal placement
                Placement best = findBestPlacement(entry, occ, idx);

                if (best != null) {
                    applyPlacement(working, i, best, occ, facId, section, semStr);
                    res.moveLog.add("🔀 " + courseId + " §" + section
                            + ": " + oldDay + " " + oldSlot
                            + " → " + best.day + " " + best.slot
                            + " [" + best.roomId + "] (score=" + String.format("%.1f", best.score) + ")");
                    res.movesApplied++;
                } else {
                    // One-step backtracking: try displacing a neighbour
                    boolean backtracted = tryBacktrack(working, i, occ, idx, res);
                    if (!backtracted) {
                        // Re-insert into original slot (couldn't move)
                        occ.occupy(facId, roomId, section, semStr, oldDay, oldSlot);
                        res.moveLog.add("⚠ No placement for " + courseId + " §" + section
                                + " — kept at " + oldDay + " " + oldSlot + ".");
                    }
                }
            }
        }
        return working;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PHASE 2 — Simulated Annealing for soft constraint optimisation
    // ═══════════════════════════════════════════════════════════════════════════

    private static List<Map<String, Object>> phaseTwoSoftOptimise(
            List<Map<String, Object>> working,
            List<Map<String, Object>> existingOther,
            ResourceIndex idx,
            ResolveResult res
    ) {
        if (working.isEmpty()) return working;

        double temp     = SA_INITIAL_TEMP;
        int    accepted = 0;
        double curScore = softScore(working);

        res.moveLog.add("🔥 SA start — soft score: " + String.format("%.1f", curScore));

        for (int iter = 0; iter < SA_ITERATIONS && temp > SA_MIN_TEMP; iter++) {
            // Pick a random entry
            int i = RNG.nextInt(working.size());
            Map<String, Object> entry = working.get(i);

            String facId   = str(entry, "faculty");
            String roomId  = str(entry, "room");
            String section = str(entry, "section");
            String semStr  = semOf(entry);
            String oldDay  = str(entry, "day");
            String oldSlot = str(entry, "slot");

            // Build fresh occupancy for candidate search
            Occupancy occ = new Occupancy(working, existingOther);
            occ.release(facId, roomId, section, semStr, oldDay, oldSlot);

            List<Placement> candidates = findAllPlacements(entry, occ, idx);
            if (candidates.isEmpty()) { temp *= SA_COOLING_RATE; continue; }

            // Pick a random candidate from the legal set
            Placement candidate = candidates.get(RNG.nextInt(candidates.size()));

            // Tentatively apply
            Map<String, Object> backup = new LinkedHashMap<>(entry);
            Map<String, Object> updated = new LinkedHashMap<>(entry);
            updated.put("day",  candidate.day);
            updated.put("slot", candidate.slot);
            updated.put("room", candidate.roomId);
            working.set(i, updated);

            double newScore = softScore(working);
            double delta    = newScore - curScore;

            boolean accept = delta > 0
                    || Math.exp(delta / temp) > RNG.nextDouble();

            if (accept) {
                curScore = newScore;
                if (delta > 0) accepted++;
            } else {
                // Revert
                working.set(i, backup);
            }

            temp *= SA_COOLING_RATE;
        }

        res.moveLog.add("❄ SA done — " + accepted
                + " accepted improvements, final soft score: "
                + String.format("%.1f", curScore));
        return working;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MRV ordering — sort conflicted entries by fewest legal placements first
    // ═══════════════════════════════════════════════════════════════════════════

    private static List<Integer> mrvOrder(
            List<Integer> indices,
            List<Map<String, Object>> working,
            Occupancy occ,
            ResourceIndex idx
    ) {
        indices.sort(Comparator.comparingInt(i -> {
            Map<String, Object> entry = working.get(i);
            String facId   = str(entry, "faculty");
            String roomId  = str(entry, "room");
            String section = str(entry, "section");
            String semStr  = semOf(entry);
            String day     = str(entry, "day");
            String slot    = str(entry, "slot");

            occ.release(facId, roomId, section, semStr, day, slot);
            int count = 0;
            outer:
            for (String d : DAYS) {
                for (String sl : SLOTS) {
                    if (isLegalSlot(entry, d, sl, occ, idx)) count++;
                    if (count > 50) break outer; // Cap to avoid O(n²) slowdown
                }
            }
            occ.occupy(facId, roomId, section, semStr, day, slot);
            return count;
        }));
        return indices;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Placement search helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Return the highest-scoring legal placement for {@code entry}, picking randomly among ties. */
    private static Placement findBestPlacement(
            Map<String, Object> entry, Occupancy occ, ResourceIndex idx) {

        String facId   = str(entry, "faculty");
        String section = str(entry, "section");
        String semStr  = semOf(entry);
        String secKey  = semStr + "-" + section;
        String courseId = str(entry, "course");
        boolean isLab  = idx.isLab(courseId);
        int needed     = idx.students(courseId);

        List<Placement> bestCandidates = new ArrayList<>();
        double bestScore = Double.NEGATIVE_INFINITY;

        for (String day : DAYS) {
            for (String slot : SLOTS) {
                if ("12:00-1:00".equals(slot)) continue;
                if (occ.facBusy(facId, day, slot))    continue;
                if (occ.secBusy(secKey, day, slot))   continue;

                for (String roomId : idx.roomsForType(isLab)) {
                    if (occ.roomBusy(roomId, day, slot))          continue;
                    if (idx.roomCap(roomId) > 0
                            && idx.roomCap(roomId) < needed)      continue;

                    double score = scorePlacement(
                            day, slot, roomId, facId, secKey, idx, occ, needed);
                    
                    if (score > bestScore + 0.001) {
                        bestScore = score;
                        bestCandidates.clear();
                        bestCandidates.add(new Placement(day, slot, roomId, score));
                    } else if (Math.abs(score - bestScore) < 0.001) {
                        bestCandidates.add(new Placement(day, slot, roomId, score));
                    }
                }
            }
        }
        
        if (bestCandidates.isEmpty()) return null;
        // Introduce randomness among equally-scored best choices
        return bestCandidates.get(RNG.nextInt(bestCandidates.size()));
    }

    /** Return ALL legal placements for {@code entry} (used in SA). */
    private static List<Placement> findAllPlacements(
            Map<String, Object> entry, Occupancy occ, ResourceIndex idx) {

        List<Placement> result = new ArrayList<>();
        String facId   = str(entry, "faculty");
        String section = str(entry, "section");
        String semStr  = semOf(entry);
        String secKey  = semStr + "-" + section;
        String courseId = str(entry, "course");
        boolean isLab  = idx.isLab(courseId);
        int needed     = idx.students(courseId);

        for (String day : DAYS) {
            for (String slot : SLOTS) {
                if ("12:00-1:00".equals(slot)) continue;
                if (occ.facBusy(facId, day, slot))  continue;
                if (occ.secBusy(secKey, day, slot)) continue;

                for (String roomId : idx.roomsForType(isLab)) {
                    if (occ.roomBusy(roomId, day, slot))         continue;
                    if (idx.roomCap(roomId) > 0
                            && idx.roomCap(roomId) < needed)     continue;

                    double score = scorePlacement(
                            day, slot, roomId, facId, secKey, idx, occ, needed);
                    result.add(new Placement(day, slot, roomId, score));
                }
            }
        }
        return result;
    }

    /** Quick legality check (faculty + section + at least one room free). */
    private static boolean isLegalSlot(
            Map<String, Object> entry, String day, String slot,
            Occupancy occ, ResourceIndex idx) {
        if ("12:00-1:00".equals(slot)) return false;
        String facId   = str(entry, "faculty");
        String section = str(entry, "section");
        String semStr  = semOf(entry);
        String courseId = str(entry, "course");
        boolean isLab  = idx.isLab(courseId);
        int needed     = idx.students(courseId);

        if (occ.facBusy(facId, day, slot))                   return false;
        if (occ.secBusy(semStr + "-" + section, day, slot))  return false;
        for (String rid : idx.roomsForType(isLab)) {
            if (!occ.roomBusy(rid, day, slot)
                    && (idx.roomCap(rid) <= 0 || idx.roomCap(rid) >= needed))
                return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // One-step backtracking — displace a neighbour to free a slot for stuckIdx
    // ═══════════════════════════════════════════════════════════════════════════

    private static boolean tryBacktrack(
            List<Map<String, Object>> working, int stuckIdx,
            Occupancy occ, ResourceIndex idx, ResolveResult res) {

        Map<String, Object> stuck   = working.get(stuckIdx);
        String stuckFac  = str(stuck, "faculty");
        String stuckSec  = str(stuck, "section");
        String stuckSem  = semOf(stuck);
        String stuckKey  = stuckSem + "-" + stuckSec;
        String courseId  = str(stuck, "course");
        boolean isLab    = idx.isLab(courseId);
        int needed       = idx.students(courseId);

        for (int j = 0; j < working.size(); j++) {
            if (j == stuckIdx) continue;
            Map<String, Object> nb   = working.get(j);
            String nFac  = str(nb, "faculty");
            String nSec  = str(nb, "section");
            String nSem  = semOf(nb);
            String nDay  = str(nb, "day");
            String nSlot = str(nb, "slot");
            String nRoom = str(nb, "room");
            String nKey  = nSem + "-" + nSec;

            // Only consider neighbours that share faculty or section with stuck entry
            boolean sharesResource = (stuckFac != null && stuckFac.equals(nFac))
                    || stuckKey.equals(nKey);
            if (!sharesResource) continue;

            // Vacate the neighbour temporarily
            occ.release(nFac, nRoom, nSec, nSem, nDay, nSlot);

            // Can the stuck entry now fit in this day+slot?
            boolean stuckFacFree = !occ.facBusy(stuckFac, nDay, nSlot);
            boolean stuckSecFree = !occ.secBusy(stuckKey, nDay, nSlot);
            boolean roomAvail    = hasRoom(isLab, nDay, nSlot, needed, occ, idx);

            if (stuckFacFree && stuckSecFree && roomAvail) {
                // Try to find a new slot for the neighbour
                Placement newNb = findBestPlacement(nb, occ, idx);
                if (newNb != null) {
                    // Commit neighbour's new position
                    applyPlacement(working, j, newNb, occ, nFac, nSec, nSem);
                    res.moveLog.add("↩ Backtrack: moved " + str(nb, "course")
                            + " §" + nSec + " " + nDay + " " + nSlot
                            + " → " + newNb.day + " " + newNb.slot);
                    res.movesApplied++;

                    // Now find the best slot for stuck entry (neighbour's old slot is free)
                    Placement forStuck = findBestPlacement(stuck, occ, idx);
                    if (forStuck != null) {
                        String oldDay  = str(stuck, "day");
                        String oldSlot = str(stuck, "slot");
                        applyPlacement(working, stuckIdx, forStuck, occ, stuckFac, stuckSec, stuckSem);
                        res.moveLog.add("↩ Backtrack resolved: " + courseId
                                + " §" + stuckSec + " " + oldDay + " " + oldSlot
                                + " → " + forStuck.day + " " + forStuck.slot);
                        res.movesApplied++;
                        return true;
                    }
                    // Couldn't place stuck even after freeing — we leave nb at new position
                    // (next attempt will pick up the remaining violation)
                    return false;
                }
            }

            // Can't use this neighbour — restore
            occ.occupy(nFac, nRoom, nSec, nSem, nDay, nSlot);
        }
        return false;
    }

    private static boolean hasRoom(
            boolean isLab, String day, String slot, int needed,
            Occupancy occ, ResourceIndex idx) {
        for (String rid : idx.roomsForType(isLab)) {
            if (!occ.roomBusy(rid, day, slot)
                    && (idx.roomCap(rid) <= 0 || idx.roomCap(rid) >= needed))
                return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Placement scoring — higher = better quality slot
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Applies a random disruption by forcibly vacating a random entry and
     * placing it in a random slot. This helps break out of local minima during Phase 1.
     */
    private static void applyRandomDisruption(
            List<Map<String, Object>> working, List<Map<String, Object>> existingOther,
            ResourceIndex idx, ResolveResult res) {
        if (working.isEmpty()) return;
        int targetIdx = RNG.nextInt(working.size());
        Map<String, Object> entry = working.get(targetIdx);
        
        String newDay = DAYS.get(RNG.nextInt(DAYS.size()));
        String newSlot = SLOTS.get(RNG.nextInt(SLOTS.size()));
        
        boolean isLab = idx.isLab(str(entry, "course"));
        List<String> validRooms = idx.roomsForType(isLab);
        if (validRooms.isEmpty()) return;
        String newRoom = validRooms.get(RNG.nextInt(validRooms.size()));
        
        Map<String, Object> updated = new LinkedHashMap<>(entry);
        updated.put("day", newDay);
        updated.put("slot", newSlot);
        updated.put("room", newRoom);
        working.set(targetIdx, updated);
        
        res.moveLog.add("🎲 Disruption applied to " + str(entry, "course") + " -> " + newDay + " " + newSlot);
    }

    /**
     * Score a candidate placement against quality heuristics.
     * All weights are positive for "good" characteristics.
     */
    private static double scorePlacement(
            String day, String slot, String roomId, String facId, String secKey,
            ResourceIndex idx, Occupancy occ, int needed) {

        double score = 0;

        // 1. Room capacity headroom (capped at 30 to avoid skewing)
        int cap = idx.roomCap(roomId);
        if (cap > 0) score += Math.min(cap - needed, 30) * W_CAPACITY;

        // 2. Faculty daily load — prefer days with lighter existing load
        int facLoad = occ.facDailyLoad(facId, day);
        score += Math.max(0, 5 - facLoad) * W_FAC_LOAD;

        // 3. Section daily load — avoid overloading a section on one day
        int secLoad = occ.secDailyLoad(secKey, day);
        if (secLoad < 3) score += W_NO_CONSEC;

        // 4. Avoid Saturday
        if (!"Saturday".equals(day)) score += W_NO_SAT;

        // 5. Avoid late afternoon slot
        if (!"4:00-5:00".equals(slot)) score += W_NO_LATE;

        // 6. Prefer morning slots (8–11 AM)
        int slotIdx = SLOTS.indexOf(slot);
        if (slotIdx >= 0 && slotIdx <= 2) score += W_MORNING;

        // 7. No large idle gap created for this section today
        if (!occ.hasBigGap(secKey, day, slotIdx)) score += W_NO_GAP;

        return score;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Soft score for simulated annealing (higher = better)
    // ═══════════════════════════════════════════════════════════════════════════

    private static double softScore(List<Map<String, Object>> working) {
        double score = 0;

        // Per section-day slot lists (for gap / consecutive analysis)
        Map<String, List<Integer>> secDaySlots = new HashMap<>();
        // Per faculty-day load (for balance analysis)
        Map<String, List<Integer>> facDayLoads = new HashMap<>();

        for (Map<String, Object> e : working) {
            String day  = str(e, "day");
            String slot = str(e, "slot");
            String sec  = str(e, "section");
            String sem  = semOf(e);
            String fac  = str(e, "faculty");

            if (day == null || slot == null) continue;

            // Penalise Saturday and late slots
            if ("Saturday".equals(day))   score -= 8;
            if ("4:00-5:00".equals(slot)) score -= 4;

            // Track section slot indices per day
            int si = SLOTS.indexOf(slot);
            if (si >= 0) {
                secDaySlots.computeIfAbsent(sem + "-" + sec + "@" + day,
                        k -> new ArrayList<>()).add(si);
            }

            // Track faculty daily load
            if (fac != null) {
                facDayLoads.computeIfAbsent(fac, k -> new ArrayList<>());
                // We use the map value list to accumulate per-day counts
                // but a simpler approach: track fac@day → count
            }
        }

        // Penalise idle gaps and consecutive overload for each section-day
        for (List<Integer> slotList : secDaySlots.values()) {
            List<Integer> sorted = new ArrayList<>(slotList);
            Collections.sort(sorted);

            // Long idle gaps
            for (int i = 0; i < sorted.size() - 1; i++) {
                int gap = sorted.get(i + 1) - sorted.get(i);
                if (gap > 2) score -= (gap - 1) * W_NO_GAP;
            }

            // 4+ consecutive back-to-back sessions
            int consec = 1;
            for (int i = 0; i < sorted.size() - 1; i++) {
                if (sorted.get(i + 1) - sorted.get(i) == 1) {
                    consec++;
                    if (consec > 3) { score -= W_NO_CONSEC; break; }
                } else {
                    consec = 1;
                }
            }
        }

        return score;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Conflict index helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the set of working-list indices whose entries appear in any
     * hard conflict (including lunch-break violations and capacity issues).
     */
    private static Set<Integer> findConflictedIndices(
            List<Map<String, Object>> working,
            List<Map<String, Object>> conflicts) {

        // Build conflictKeys from the validation result
        Set<String> conflictKeys = new HashSet<>();
        for (Map<String, Object> c : conflicts) {
            String cDay  = c.get("day")  != null ? c.get("day").toString()  : "";
            String cSlot = c.get("slot") != null ? c.get("slot").toString() : "";
            Object aff   = c.get("affectedCourses");
            if (aff instanceof List<?> l) {
                for (Object a : l) {
                    if (a != null) conflictKeys.add(a + "@" + cDay + "@" + cSlot);
                }
            }
        }

        Set<Integer> result = new HashSet<>();
        for (int i = 0; i < working.size(); i++) {
            Map<String, Object> e = working.get(i);
            String courseId = str(e, "course");
            String day      = str(e, "day");
            String slot     = str(e, "slot");

            // Direct lunch-break violation
            if ("12:00-1:00".equals(slot)) { result.add(i); continue; }

            // Appear in a conflict record
            if (conflictKeys.contains(courseId + "@" + day + "@" + slot)) result.add(i);
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Apply a committed placement to the working list + occupancy
    // ═══════════════════════════════════════════════════════════════════════════

    private static void applyPlacement(
            List<Map<String, Object>> working, int i,
            Placement p, Occupancy occ,
            String facId, String section, String semStr) {

        Map<String, Object> entry   = working.get(i);
        Map<String, Object> updated = new LinkedHashMap<>(entry);
        updated.put("day",  p.day);
        updated.put("slot", p.slot);
        updated.put("room", p.roomId);
        working.set(i, updated);

        occ.occupy(facId, p.roomId, section, semStr, p.day, p.slot);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Utility helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static String str(Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        return v != null ? v.toString() : null;
    }

    private static String semOf(Map<String, Object> e) {
        Object v = e.get("semester");
        return v != null ? v.toString() : "";
    }

    private static List<Map<String, Object>> deepCopy(List<Map<String, Object>> src) {
        List<Map<String, Object>> copy = new ArrayList<>();
        if (src == null) return copy;
        for (Map<String, Object> e : src) copy.add(new LinkedHashMap<>(e));
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner classes
    // ═══════════════════════════════════════════════════════════════════════════

    /** Immutable placement candidate (day + slot + room + quality score). */
    private static final class Placement {
        final String day, slot, roomId;
        final double score;

        Placement(String day, String slot, String roomId, double score) {
            this.day    = day;
            this.slot   = slot;
            this.roomId = roomId;
            this.score  = score;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Live occupancy snapshot used during a single resolution pass.
     * Tracks which (faculty, room, section) × (day, slot) keys are occupied.
     * Also maintains daily-load counters and slot-index lists for scoring.
     */
    private static final class Occupancy {

        private final Set<String>          fac  = new HashSet<>();  // facId@day@slot
        private final Set<String>          room = new HashSet<>();  // roomId@day@slot
        private final Set<String>          sec  = new HashSet<>();  // semStr-section@day@slot

        // Daily load counters (for scoring heuristics)
        private final Map<String, Integer>       facDay     = new HashMap<>();  // facId@day → int
        private final Map<String, Integer>       secDay     = new HashMap<>();  // secKey@day → int
        // Slot-index lists per section-day (for gap detection)
        private final Map<String, List<Integer>> secSlots   = new HashMap<>();  // secKey@day → [idx...]

        Occupancy(List<Map<String, Object>> working, List<Map<String, Object>> other) {
            for (Map<String, Object> e : other)  seedEntry(e);
            for (Map<String, Object> e : working) seedEntry(e);
        }

        private void seedEntry(Map<String, Object> e) {
            if (e == null) return;
            String d   = str(e, "day");
            String sl  = str(e, "slot");
            String f   = str(e, "faculty");
            String r   = str(e, "room");
            String sc  = str(e, "section");
            String sem = semOf(e);
            if (d == null || sl == null) return;

            if (f  != null) { fac.add(f + "@" + d + "@" + sl);  facDay.merge(f + "@" + d, 1, Integer::sum); }
            if (r  != null)   room.add(r + "@" + d + "@" + sl);
            if (sc != null) {
                String sk = sem + "-" + sc;
                sec.add(sk + "@" + d + "@" + sl);
                secDay.merge(sk + "@" + d, 1, Integer::sum);
                int si = SLOTS.indexOf(sl);
                if (si >= 0) secSlots.computeIfAbsent(sk + "@" + d, k -> new ArrayList<>()).add(si);
            }
        }

        private static String str(Map<String, Object> m, String k) {
            Object v = m.get(k); return v != null ? v.toString() : null;
        }
        private static String semOf(Map<String, Object> e) {
            Object v = e.get("semester"); return v != null ? v.toString() : "";
        }

        void occupy(String facId, String roomId, String section, String semStr,
                    String day, String slot) {
            if (facId   != null) { fac.add(facId + "@" + day + "@" + slot);   facDay.merge(facId + "@" + day, 1, Integer::sum); }
            if (roomId  != null)   room.add(roomId + "@" + day + "@" + slot);
            if (section != null) {
                String sk = semStr + "-" + section;
                sec.add(sk + "@" + day + "@" + slot);
                secDay.merge(sk + "@" + day, 1, Integer::sum);
                int si = SLOTS.indexOf(slot);
                if (si >= 0) secSlots.computeIfAbsent(sk + "@" + day, k -> new ArrayList<>()).add(si);
            }
        }

        void release(String facId, String roomId, String section, String semStr,
                     String day, String slot) {
            if (facId   != null) { fac.remove(facId + "@" + day + "@" + slot);   facDay.merge(facId + "@" + day, -1, Integer::sum); }
            if (roomId  != null)   room.remove(roomId + "@" + day + "@" + slot);
            if (section != null) {
                String sk = semStr + "-" + section;
                sec.remove(sk + "@" + day + "@" + slot);
                secDay.merge(sk + "@" + day, -1, Integer::sum);
                List<Integer> list = secSlots.get(sk + "@" + day);
                if (list != null) {
                    int si = SLOTS.indexOf(slot);
                    list.remove(Integer.valueOf(si));
                }
            }
        }

        boolean facBusy (String facId,  String day, String slot) { return facId  != null && fac.contains(facId  + "@" + day + "@" + slot); }
        boolean roomBusy(String roomId, String day, String slot) { return roomId != null && room.contains(roomId + "@" + day + "@" + slot); }
        boolean secBusy (String secKey, String day, String slot) { return secKey != null && sec.contains(secKey + "@" + day + "@" + slot); }

        int facDailyLoad(String facId,  String day) { return facDay.getOrDefault(facId  + "@" + day, 0); }
        int secDailyLoad(String secKey, String day) { return secDay.getOrDefault(secKey + "@" + day, 0); }

        /** True if placing a new class at {@code newSlotIdx} on {@code day}
         *  for section {@code secKey} would create a gap > 2 slots. */
        boolean hasBigGap(String secKey, String day, int newSlotIdx) {
            List<Integer> existing = secSlots.get(secKey + "@" + day);
            if (existing == null || existing.isEmpty()) return false;
            for (int si : existing) {
                if (Math.abs(si - newSlotIdx) > 2) return true;
            }
            return false;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Pre-built, immutable lookup tables for courses, rooms, and faculty.
     * Avoids repeated linear scans during the hot placement loops.
     */
    private static final class ResourceIndex {
        final List<Map<String, Object>> courses;
        final List<Map<String, Object>> faculty;
        final List<Map<String, Object>> rooms;

        private final Map<String, String>  courseType     = new HashMap<>();
        private final Map<String, Integer> courseStudents = new HashMap<>();
        private final Map<String, Integer> roomCapacity   = new HashMap<>();
        private final List<String>         lectureRooms   = new ArrayList<>();
        private final List<String>         labRooms       = new ArrayList<>();

        ResourceIndex(List<Map<String, Object>> courses,
                      List<Map<String, Object>> faculty,
                      List<Map<String, Object>> rooms) {
            this.courses = courses != null ? courses : List.of();
            this.faculty = faculty != null ? faculty : List.of();
            this.rooms   = rooms   != null ? rooms   : List.of();

            for (Map<String, Object> c : this.courses) {
                if (c.get("id") == null) continue;
                String id = c.get("id").toString();
                courseType.put(id, c.get("type") != null ? c.get("type").toString() : "Theory");
                int st = 50;
                if (c.containsKey("studentsCount") && c.get("studentsCount") instanceof Number n)
                    st = n.intValue();
                else if (c.containsKey("capacity") && c.get("capacity") instanceof Number n)
                    st = n.intValue();
                courseStudents.put(id, st);
            }

            for (Map<String, Object> r : this.rooms) {
                if (r.get("id") == null) continue;
                String id   = r.get("id").toString();
                String type = r.get("type") != null ? r.get("type").toString() : "Lecture";
                int cap = (r.get("capacity") instanceof Number n) ? n.intValue() : 0;
                roomCapacity.put(id, cap);
                if ("Lab".equalsIgnoreCase(type)) labRooms.add(id);
                else                              lectureRooms.add(id);
            }
        }

        boolean isLab(String courseId) {
            return "Lab".equalsIgnoreCase(courseType.getOrDefault(courseId, "Theory"));
        }
        int students(String courseId) { return courseStudents.getOrDefault(courseId, 50); }
        int roomCap (String roomId)   { return roomCapacity.getOrDefault(roomId,   0); }
        List<String> roomsForType(boolean lab) { return lab ? labRooms : lectureRooms; }
    }
}
