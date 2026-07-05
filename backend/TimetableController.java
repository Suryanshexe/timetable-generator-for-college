import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class TimetableController {

    private static final Path TIMETABLE_FILE = Path.of("backend", "data", "timetable.json");
    private static final Path COURSES_FILE = Path.of("backend", "data", "courses.json");
    private static final Path FACULTY_FILE = Path.of("backend", "data", "faculty.json");
    private static final Path ROOMS_FILE = Path.of("backend", "data", "rooms.json");

    private static final List<String> DAYS = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
    private static final List<String> SLOTS = List.of("8:00-9:00", "9:00-10:00", "10:00-11:00", "11:00-12:00", "12:00-1:00", "2:00-3:00", "3:00-4:00", "4:00-5:00");

    // Load timetable
    public static String getTimetable() throws IOException {
        if (!Files.exists(TIMETABLE_FILE)) {
            // Seed a sample timetable matching the frontend initial state
            String seed = getSampleTimetableJson();
            Files.createDirectories(TIMETABLE_FILE.getParent());
            Files.writeString(TIMETABLE_FILE, seed, StandardCharsets.UTF_8);
            return seed;
        }
        return Files.readString(TIMETABLE_FILE, StandardCharsets.UTF_8);
    }

    // Save timetable after backend validation
    public static String saveTimetable(String timetableJson) throws IOException {
        List<Map<String, Object>> newEntries = parseList(timetableJson);
        List<Map<String, Object>> existing = loadDataList(TIMETABLE_FILE);
        List<Map<String, Object>> merged = new ArrayList<>();

        if (!newEntries.isEmpty()) {
            Set<String> incomingCombos = new HashSet<>();
            for (Map<String, Object> entry : newEntries) {
                String d = (String) entry.get("dept");
                String sem = entry.get("semester").toString();
                String sec = (String) entry.get("section");
                incomingCombos.add(d.toLowerCase() + "@" + sem + "@" + sec.toLowerCase());
            }

            if (incomingCombos.size() == 1) {
                String targetCombo = incomingCombos.iterator().next();
                for (Map<String, Object> entry : existing) {
                    String d = (String) entry.get("dept");
                    String sem = entry.get("semester").toString();
                    String sec = (String) entry.get("section");
                    String combo = d.toLowerCase() + "@" + sem + "@" + sec.toLowerCase();
                    if (!combo.equals(targetCombo)) {
                        merged.add(entry);
                    }
                }
                merged.addAll(newEntries);
            } else {
                merged = newEntries;
            }
        } else {
            merged = newEntries;
        }

        List<Map<String, Object>> courses = loadDataList(COURSES_FILE);
        List<Map<String, Object>> faculty = loadDataList(FACULTY_FILE);
        List<Map<String, Object>> rooms = loadDataList(ROOMS_FILE);

        ValidationEngine.ValidationResult validation = ValidationEngine.validate(merged, courses, faculty, rooms);

        // Save file
        Files.createDirectories(TIMETABLE_FILE.getParent());
        Files.writeString(TIMETABLE_FILE, JsonUtil.toJson(merged), StandardCharsets.UTF_8);

        // Return status with validation details
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "saved");
        resp.put("hardViolations", validation.hardCount);
        resp.put("softViolations", validation.softCount);
        resp.put("overallScore", validation.overallScore);
        resp.put("conflicts", validation.conflicts);
        resp.put("softConstraintViolations", validation.softViolations);

        return JsonUtil.toJson(resp);
    }

    // Clear timetable by semester / dept
    public static String clearTimetable(String paramsJson) throws IOException {
        Map<String, Object> params = (Map<String, Object>) JsonUtil.parse(paramsJson);
        String semester = params.containsKey("semester") ? params.get("semester").toString() : "All";
        String dept = params.containsKey("dept") ? (String) params.get("dept") : "All";

        List<Map<String, Object>> existing = loadDataList(TIMETABLE_FILE);
        List<Map<String, Object>> remaining = new ArrayList<>();

        if (!"All".equalsIgnoreCase(semester)) {
            for (Map<String, Object> entry : existing) {
                String entrySem = entry.get("semester").toString();
                String entryDept = (String) entry.get("dept");
                
                boolean matchSem = semester.equals(entrySem);
                boolean matchDept = "All".equalsIgnoreCase(dept) || dept.equalsIgnoreCase(entryDept);
                
                if (!(matchSem && matchDept)) {
                    remaining.add(entry);
                }
            }
        } // if "All" is selected, remaining is left empty so we clear everything

        Files.createDirectories(TIMETABLE_FILE.getParent());
        Files.writeString(TIMETABLE_FILE, JsonUtil.toJson(remaining), StandardCharsets.UTF_8);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "success");
        resp.put("cleared", existing.size() - remaining.size());
        resp.put("remaining", remaining.size());

        return JsonUtil.toJson(resp);
    }

    // Validate request
    public static String validateTimetable(String timetableJson) throws IOException {
        List<Map<String, Object>> timetable = parseList(timetableJson);
        List<Map<String, Object>> courses = loadDataList(COURSES_FILE);
        List<Map<String, Object>> faculty = loadDataList(FACULTY_FILE);
        List<Map<String, Object>> rooms = loadDataList(ROOMS_FILE);

        ValidationEngine.ValidationResult result = ValidationEngine.validate(timetable, courses, faculty, rooms);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("hardViolations", result.hardCount);
        resp.put("softViolations", result.softCount);
        resp.put("overallScore", result.overallScore);
        resp.put("conflicts", result.conflicts);
        resp.put("softConstraintViolations", result.softViolations);

        // Generate analytics summary for faculty workload and room utilization
        resp.put("facultyWorkload", calculateFacultyWorkload(timetable, faculty));
        resp.put("roomUtilization", calculateRoomUtilization(timetable, rooms));

        return JsonUtil.toJson(resp);
    }

    // AI automatic slot generation
    public static String generateTimetable(String paramsJson) throws Exception {
        Map<String, Object> params = (Map<String, Object>) JsonUtil.parse(paramsJson);
        String dept = (String) params.get("dept");
        String semester = params.get("semester").toString();
        String section = (String) params.get("section");
        String algorithm = (String) params.get("algorithm");
        Map<String, Object> constraints = (Map<String, Object>) params.get("constraints");
        String customCommand = (String) params.get("customCommand");
        String scheduleType = params.containsKey("scheduleType") ? params.get("scheduleType").toString() : "tight";

        List<Map<String, Object>> courses = loadDataList(COURSES_FILE);
        List<Map<String, Object>> faculty = loadDataList(FACULTY_FILE);
        List<Map<String, Object>> rooms = loadDataList(ROOMS_FILE);

        // Filter courses to only include those for the target semester and department
        List<Map<String, Object>> filteredCourses = new ArrayList<>();
        for (Map<String, Object> c : courses) {
            String cDept = (String) c.get("dept");
            String cSem = c.get("semester").toString();
            if (dept.equalsIgnoreCase(cDept) && semester.equals(cSem)) {
                filteredCourses.add(c);
            }
        }

        String apiKey = GroqClient.getApiKey();
        boolean useAI = "ai".equalsIgnoreCase(algorithm) && !apiKey.isEmpty();

        if (useAI) {
            try {
                return generateWithGroq(filteredCourses, faculty, rooms, dept, semester, section, constraints, customCommand, scheduleType);
            } catch (Exception e) {
                System.err.println("Groq timetable generation failed. Falling back to local solver. Error: " + e.getMessage());
                e.printStackTrace();
                // Fall back to local solver
            }
        }

        // Fallback local solver
        return generateWithLocalSolver(filteredCourses, faculty, rooms, dept, semester, section, constraints, scheduleType);
    }

    // Call Groq Llama 3.1 API to generate timetable via AIService
    private static String generateWithGroq(
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms,
            String dept, String semester, String section,
            Map<String, Object> constraints,
            String customCommand,
            String scheduleType
    ) throws Exception {
        return AIService.generate(courses, faculty, rooms, dept, semester, section, constraints, customCommand, DAYS, SLOTS, scheduleType);
    }


    interface FreeChecker {
        boolean test(String facId, String roomId, String secKey, String day, String slot);
    }

    // Local Greedy constraint solver fallback
    private static String generateWithLocalSolver(
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms,
            String dept, String semester, String section,
            Map<String, Object> constraints,
            String scheduleType
    ) {
        List<Map<String, Object>> timetable = new ArrayList<>();
        Map<String, Integer> roomClassCount = new HashMap<>();
        
        // Define color palette for visual layout
        String[] colors = {"#1a4a8a", "#c0392b", "#1a7a46", "#b7620a", "#5a2d82", "#0d6e7a", "#e67e22", "#2ecc71", "#3498db"};
        int colorIdx = 0;

        // Track occupied slots
        // Key: facId + "@" + day + "@" + slot
        Set<String> occupiedFac = new HashSet<>();
        // Key: roomId + "@" + day + "@" + slot
        Set<String> occupiedRoom = new HashSet<>();
        // Key: sem-sec + "@" + day + "@" + slot
        Set<String> occupiedSec = new HashSet<>();

        // Helper to check availability
        FreeChecker isFree = (String facId, String roomId, String secKey, String day, String slot) -> {
            if ("12:00-1:00".equals(slot)) return false; // Lunch break
            if (occupiedFac.contains(facId + "@" + day + "@" + slot)) return false;
            if (occupiedRoom.contains(roomId + "@" + day + "@" + slot)) return false;
            if (occupiedSec.contains(secKey + "@" + day + "@" + slot)) return false;
            return true;
        };


        // Filter courses for our target department, semester, and section
        for (Map<String, Object> course : courses) {
            String cDept = (String) course.get("dept");
            String cSem = course.get("semester").toString();
            List<?> cSections = (List<?>) course.get("sections");

            if (!dept.equalsIgnoreCase(cDept) || !semester.equals(cSem)) {
                continue; // Not target
            }

            // Determine sections to schedule
            List<String> targetSecs = new ArrayList<>();
            if ("All".equalsIgnoreCase(section)) {
                for (Object s : cSections) targetSecs.add(s.toString());
            } else if (cSections.contains(section)) {
                targetSecs.add(section);
            } else {
                continue;
            }

            // Find assigned faculty
            String facId = null;
            for (Map<String, Object> f : faculty) {
                List<?> fCourses = (List<?>) f.get("courses");
                if (fCourses.contains(course.get("id"))) {
                    facId = (String) f.get("id");
                    break;
                }
            }
            if (facId == null) facId = "F01"; // Fallback faculty assignment

            String courseType = (String) course.get("type");
            long credits = course.containsKey("credits") ? ((Number) course.get("credits")).longValue() : 3;
            String courseColor = colors[colorIdx % colors.length];
            colorIdx++;

            // Schedule for each target section
            for (String sec : targetSecs) {
                String secKey = semester + "-" + sec;
                long hoursNeeded = credits;

                if ("Lab".equalsIgnoreCase(courseType)) {
                    // Schedule lab blocks of 2 hours
                    int labBlocks = (int) (hoursNeeded / 2);
                    for (int lb = 0; lb < labBlocks; lb++) {
                        boolean scheduledBlock = false;
                        for (String day : DAYS) {
                            if (scheduledBlock) break;
                            for (int i = 0; i < SLOTS.size() - 1; i++) {
                                String slot1 = SLOTS.get(i);
                                String slot2 = SLOTS.get(i + 1);
                                if ("12:00-1:00".equals(slot1) || "12:00-1:00".equals(slot2)) continue; // skip lunch
                                
                                // Balance room utilization by sorting by class count
                                List<Map<String, Object>> sortedRooms = new ArrayList<>(rooms);
                                sortedRooms.sort(Comparator.comparingInt(r -> roomClassCount.getOrDefault(r.get("id").toString(), 0)));

                                // Find a suitable lab room
                                for (Map<String, Object> room : sortedRooms) {
                                    if (!"Lab".equalsIgnoreCase((String) room.get("type"))) continue;
                                    String roomId = (String) room.get("id");
                                    
                                    if (isFree.test(facId, roomId, secKey, day, slot1) && isFree.test(facId, roomId, secKey, day, slot2)) {
                                        // Book slots
                                        timetable.add(createEntry(day, slot1, (String) course.get("id"), facId, roomId, sec, semester, dept, courseColor));
                                        timetable.add(createEntry(day, slot2, (String) course.get("id"), facId, roomId, sec, semester, dept, courseColor));
                                        
                                        occupiedFac.add(facId + "@" + day + "@" + slot1);
                                        occupiedFac.add(facId + "@" + day + "@" + slot2);
                                        occupiedRoom.add(roomId + "@" + day + "@" + slot1);
                                        occupiedRoom.add(roomId + "@" + day + "@" + slot2);
                                        occupiedSec.add(secKey + "@" + day + "@" + slot1);
                                        occupiedSec.add(secKey + "@" + day + "@" + slot2);
                                        
                                        roomClassCount.put(roomId, roomClassCount.getOrDefault(roomId, 0) + 2);

                                        if ("easy".equalsIgnoreCase(scheduleType)) {
                                            int idx = SLOTS.indexOf(slot2);
                                            if (idx != -1 && idx + 1 < SLOTS.size()) {
                                                String afterSlot = SLOTS.get(idx + 1);
                                                occupiedFac.add(facId + "@" + day + "@" + afterSlot);
                                                occupiedSec.add(secKey + "@" + day + "@" + afterSlot);
                                            }
                                        }

                                        scheduledBlock = true;
                                        break;
                                    }
                                }
                                if (scheduledBlock) break;
                            }
                        }
                    }
                } else {
                    // Schedule theory lectures (1-hour slots)
                    for (int h = 0; h < hoursNeeded; h++) {
                        boolean scheduledSlot = false;
                        for (String day : DAYS) {
                            if (scheduledSlot) break;
                            for (String slot : SLOTS) {
                                if ("12:00-1:00".equals(slot)) continue;
                                
                                // Balance room utilization by sorting by class count
                                List<Map<String, Object>> sortedRooms = new ArrayList<>(rooms);
                                sortedRooms.sort(Comparator.comparingInt(r -> roomClassCount.getOrDefault(r.get("id").toString(), 0)));

                                // Find lecture room
                                for (Map<String, Object> room : sortedRooms) {
                                    if ("Lab".equalsIgnoreCase((String) room.get("type"))) continue;
                                    String roomId = (String) room.get("id");
                                    
                                    if (isFree.test(facId, roomId, secKey, day, slot)) {
                                        timetable.add(createEntry(day, slot, (String) course.get("id"), facId, roomId, sec, semester, dept, courseColor));
                                        
                                        occupiedFac.add(facId + "@" + day + "@" + slot);
                                        occupiedRoom.add(roomId + "@" + day + "@" + slot);
                                        occupiedSec.add(secKey + "@" + day + "@" + slot);
                                        
                                        roomClassCount.put(roomId, roomClassCount.getOrDefault(roomId, 0) + 1);

                                        if ("easy".equalsIgnoreCase(scheduleType)) {
                                            int idx = SLOTS.indexOf(slot);
                                            if (idx != -1 && idx + 1 < SLOTS.size()) {
                                                String nextSlot = SLOTS.get(idx + 1);
                                                occupiedFac.add(facId + "@" + day + "@" + nextSlot);
                                                occupiedSec.add(secKey + "@" + day + "@" + nextSlot);
                                            }
                                        }

                                        scheduledSlot = true;
                                        break;
                                    }
                                }
                                if (scheduledSlot) break;
                            }
                        }
                    }
                }
            }
        }

        // Run validation
        ValidationEngine.ValidationResult result = ValidationEngine.validate(timetable, courses, faculty, rooms);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("timetable", timetable);
        resp.put("conflicts", result.conflicts);
        resp.put("softConstraintViolations", result.softViolations);
        resp.put("facultyWorkload", calculateFacultyWorkload(timetable, faculty));
        resp.put("roomUtilization", calculateRoomUtilization(timetable, rooms));

        Map<String, Object> scoreMap = new LinkedHashMap<>();
        scoreMap.put("hardViolations", result.hardCount);
        scoreMap.put("softViolations", result.softCount);
        scoreMap.put("overallScore", result.overallScore);
        resp.put("score", scoreMap);
        resp.put("generator", "Local Greedy constraint solver");

        return JsonUtil.toJson(resp);
    }

    private static Map<String, Object> createEntry(String day, String slot, String course, String faculty, String room, String section, String semester, String dept, String color) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("day", day);
        entry.put("slot", slot);
        entry.put("course", course);
        entry.put("faculty", faculty);
        entry.put("room", room);
        entry.put("section", section);
        entry.put("semester", Integer.parseInt(semester));
        entry.put("dept", dept);
        entry.put("color", color);
        return entry;
    }

    // AI suggestions helper (Features 2, 7)
    public static String getSuggestions(String requestBody) throws Exception {
        Map<String, Object> body = (Map<String, Object>) JsonUtil.parse(requestBody);
        
        List<Map<String, Object>> courses = loadDataList(COURSES_FILE);
        List<Map<String, Object>> faculty = loadDataList(FACULTY_FILE);
        List<Map<String, Object>> rooms = loadDataList(ROOMS_FILE);
        
        return getLocalSuggestions(body, courses, faculty, rooms);
    }

    private static String getLocalSuggestions(
            Map<String, Object> request,
            List<Map<String, Object>> courses,
            List<Map<String, Object>> faculty,
            List<Map<String, Object>> rooms
    ) {
        // Fallback local suggestions finder
        List<Map<String, Object>> suggestions = new ArrayList<>();
        Map<String, Object> entry = (Map<String, Object>) request.get("entry");
        List<Map<String, Object>> currentTimetable = (List<Map<String, Object>>) request.get("currentTimetable");

        if (entry == null || currentTimetable == null) {
            return "{\"suggestions\":[]}";
        }

        String courseId = (String) entry.get("course");
        String facultyId = (String) entry.get("faculty");
        String roomId = (String) entry.get("room");

        // Determine course type to find compatible rooms
        String courseType = "Lecture";
        for (Map<String, Object> c : courses) {
            if (courseId.equals(c.get("id"))) {
                courseType = (String) c.get("type");
                break;
            }
        }
        boolean isLab = "Lab".equalsIgnoreCase(courseType);

        // Find a day and slot where room, faculty and student section are free
        Set<String> busyFac = new HashSet<>();
        Set<String> busyRoom = new HashSet<>();
        Set<String> busySec = new HashSet<>();

        String targetSec = entry.get("section").toString();
        String targetSem = entry.get("semester").toString();

        for (Map<String, Object> item : currentTimetable) {
            String day = (String) item.get("day");
            String slot = (String) item.get("slot");
            busyFac.add(item.get("faculty") + "@" + day + "@" + slot);
            busyRoom.add(item.get("room") + "@" + day + "@" + slot);
            busySec.add(item.get("semester") + "-" + item.get("section") + "@" + day + "@" + slot);
        }

        outerLoop:
        for (String day : DAYS) {
            for (String slot : SLOTS) {
                if ("12:00-1:00".equals(slot)) continue;
                
                String facKey = facultyId + "@" + day + "@" + slot;
                String secKey = targetSem + "-" + targetSec + "@" + day + "@" + slot;

                // Check if faculty and section are free in this slot
                if (!busyFac.contains(facKey) && !busySec.contains(secKey)) {
                    // Find a compatible room that is free
                    for (Map<String, Object> room : rooms) {
                        String rType = (String) room.get("type");
                        boolean match = isLab ? "Lab".equalsIgnoreCase(rType) : !"Lab".equalsIgnoreCase(rType);
                        if (!match) continue;

                        String rId = (String) room.get("id");
                        String roomKey = rId + "@" + day + "@" + slot;

                        if (!busyRoom.contains(roomKey)) {
                            Map<String, Object> sug = new LinkedHashMap<>();
                            sug.put("action", "Move " + courseId + " " + entry.get("day") + " " + entry.get("slot") + " to " + day + " " + slot + " in room " + rId);
                            sug.put("reason", "Faculty and section are free, and room " + rId + " is available, causing 0 violations.");
                            sug.put("newDay", day);
                            sug.put("newSlot", slot);
                            sug.put("newRoom", rId);
                            suggestions.add(sug);
                            
                            if (suggestions.size() >= 5) break outerLoop;
                            break; // Stop searching rooms for this slot
                        }
                    }
                }
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("suggestions", suggestions);
        return JsonUtil.toJson(resp);
    }

    // Natural language command update (Feature 6)
    public static String handleChatCommand(String requestBody) throws Exception {
        Map<String, Object> body = (Map<String, Object>) JsonUtil.parse(requestBody);
        String command = (String) body.get("command");
        List<Map<String, Object>> currentTimetable = (List<Map<String, Object>>) body.get("currentTimetable");

        List<Map<String, Object>> courses = loadDataList(COURSES_FILE);
        List<Map<String, Object>> faculty = loadDataList(FACULTY_FILE);
        List<Map<String, Object>> rooms = loadDataList(ROOMS_FILE);

        String apiKey = GroqClient.getApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("AI Command feature requires GROQ_API_KEY to be configured.");
        }

        String systemPrompt = "You are an AI Timetable Assistant.\n" +
                "You have access to the current timetable, registries, and days/slots.\n" +
                "The user will either ask a question about the timetable/schedule/registries, OR issue a command to modify the timetable.\n" +
                "Your task is to determine the user's intent and respond with a JSON object matching the following schema:\n" +
                "{\n" +
                "  \"type\": \"update\" or \"question\",\n" +
                "  \"timetable\": [optional, updated timetable array if type is 'update'],\n" +
                "  \"answer\": \"your natural language answer to the user's question, if type is 'question'\",\n" +
                "  \"message\": \"a short summary of the action taken (e.g. 'Moved CS301' or 'Answered question')\"\n" +
                "}\n" +
                "Rules:\n" +
                "- If the user asks a question, set type to 'question', and write your helpful, accurate answer in the 'answer' field based on the current timetable/registries. Do not change the timetable.\n" +
                "- If the user issues a command to modify the timetable (e.g., add/remove/move/reschedule/swap classes), set type to 'update', modify the 'timetable' array accordingly, and set the 'message' field.\n" +
                "- Return valid JSON only.";

        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("command", command);
        userPayload.put("currentTimetable", currentTimetable);
        userPayload.put("courses", courses);
        userPayload.put("faculty", faculty);
        userPayload.put("rooms", rooms);
        userPayload.put("days", DAYS);
        userPayload.put("slots", SLOTS);

        String aiResponse = GroqClient.callGroq(systemPrompt, JsonUtil.toJson(userPayload));

        Map<String, Object> parsed = (Map<String, Object>) JsonUtil.parse(aiResponse);
        String type = (String) parsed.get("type");

        if ("update".equals(type)) {
            List<Map<String, Object>> updatedTimetable = (List<Map<String, Object>>) parsed.get("timetable");
            if (updatedTimetable != null) {
                ValidationEngine.ValidationResult result = ValidationEngine.validate(updatedTimetable, courses, faculty, rooms);

                parsed.put("conflicts", result.conflicts);
                parsed.put("softConstraintViolations", result.softViolations);
                parsed.put("facultyWorkload", calculateFacultyWorkload(updatedTimetable, faculty));
                parsed.put("roomUtilization", calculateRoomUtilization(updatedTimetable, rooms));

                Map<String, Object> scoreMap = new LinkedHashMap<>();
                scoreMap.put("hardViolations", result.hardCount);
                scoreMap.put("softViolations", result.softCount);
                scoreMap.put("overallScore", result.overallScore);
                parsed.put("score", scoreMap);
            }
        }

        return JsonUtil.toJson(parsed);
    }

    // CSV imports (Feature 1)
    public static String handleCsvImport(String collection, String csvContent) throws IOException {
        String[] lines = csvContent.split("\\r?\\n");
        if (lines.length <= 1) {
            throw new IllegalArgumentException("CSV content is empty or contains header only.");
        }

        String[] headers = lines[0].split(",");
        List<Map<String, Object>> importedItems = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] values = splitCsvLine(line);
            Map<String, Object> item = new LinkedHashMap<>();

            for (int h = 0; h < headers.length; h++) {
                if (h >= values.length) continue;
                String header = headers[h].trim().replace("\"", "");
                String val = values[h].trim().replace("\"", "");

                if ("capacity".equalsIgnoreCase(header) || "credits".equalsIgnoreCase(header) || "semester".equalsIgnoreCase(header) || "studentsCount".equalsIgnoreCase(header)) {
                    try {
                        item.put(header, Double.parseDouble(val));
                    } catch (NumberFormatException e) {
                        item.put(header, val);
                    }
                } else if ("sections".equalsIgnoreCase(header) || "courses".equalsIgnoreCase(header) || "unavailableSlots".equalsIgnoreCase(header)) {
                    // Parse arrays from string separated by semicolon
                    String[] arr = val.split(";");
                    List<String> list = new ArrayList<>();
                    for (String a : arr) {
                        if (!a.trim().isEmpty()) list.add(a.trim());
                    }
                    item.put(header, list);
                } else {
                    item.put(header, val);
                }
            }
            if (item.containsKey("id")) {
                importedItems.add(item);
            }
        }

        // Save imported items to their file
        Path targetFile = switch (collection) {
            case "courses" -> COURSES_FILE;
            case "faculty" -> FACULTY_FILE;
            case "rooms" -> ROOMS_FILE;
            default -> throw new IllegalArgumentException("Invalid import collection: " + collection);
        };

        // Merge or replace. Let's merge with existing
        List<Map<String, Object>> existing = loadDataList(targetFile);
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> ex : existing) {
            map.put(ex.get("id").toString(), ex);
        }
        for (Map<String, Object> imp : importedItems) {
            map.put(imp.get("id").toString(), imp);
        }

        List<Map<String, Object>> merged = new ArrayList<>(map.values());
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, JsonUtil.toJson(merged), StandardCharsets.UTF_8);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("importedCount", importedItems.size());
        result.put("totalCount", merged.size());
        return JsonUtil.toJson(result);
    }

    private static String[] splitCsvLine(String line) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString());
        return list.toArray(new String[0]);
    }

    // Analytics helpers
    public static List<Map<String, Object>> calculateFacultyWorkload(List<Map<String, Object>> timetable, List<Map<String, Object>> facultyList) {
        List<Map<String, Object>> workloads = new ArrayList<>();
        
        Map<String, Map<String, Object>> facultyMap = new HashMap<>();
        for (Map<String, Object> f : facultyList) {
            facultyMap.put(f.get("id").toString(), f);
        }

        // Count lectures for each faculty
        Map<String, Integer> lecturesCount = new HashMap<>();
        Map<String, Integer> labHoursCount = new HashMap<>();
        // Key: facultyId -> map of day -> count
        Map<String, Map<String, Integer>> dailyDistribution = new HashMap<>();

        for (Map<String, Object> entry : timetable) {
            String facId = (String) entry.get("faculty");
            String day = (String) entry.get("day");
            String room = (String) entry.get("room"); // to check labs

            lecturesCount.put(facId, lecturesCount.getOrDefault(facId, 0) + 1);
            if (room != null && room.toLowerCase().contains("lab")) {
                labHoursCount.put(facId, labHoursCount.getOrDefault(facId, 0) + 1);
            }

            dailyDistribution.computeIfAbsent(facId, k -> new HashMap<>())
                    .put(day, dailyDistribution.get(facId).getOrDefault(day, 0) + 1);
        }

        for (Map<String, Object> fac : facultyList) {
            String id = fac.get("id").toString();
            int total = lecturesCount.getOrDefault(id, 0);
            int labs = labHoursCount.getOrDefault(id, 0);
            int busy = total;
            int free = DAYS.size() * (SLOTS.size() - 1) - busy; // 1 slot is lunch

            Map<String, Object> wl = new LinkedHashMap<>();
            wl.put("facultyId", id);
            wl.put("facultyName", fac.get("name").toString());
            wl.put("totalLectures", total);
            wl.put("labHours", labs);
            wl.put("freeSlots", free);
            wl.put("busySlots", busy);
            wl.put("dailyWorkload", dailyDistribution.getOrDefault(id, Map.of()));
            workloads.add(wl);
        }

        return workloads;
    }

    public static List<Map<String, Object>> calculateRoomUtilization(List<Map<String, Object>> timetable, List<Map<String, Object>> roomsList) {

        List<Map<String, Object>> utilization = new ArrayList<>();

        Map<String, Integer> usage = new HashMap<>();
        for (Map<String, Object> entry : timetable) {
            String roomId = (String) entry.get("room");
            usage.put(roomId, usage.getOrDefault(roomId, 0) + 1);
        }

        int totalAvailableSlots = DAYS.size() * (SLOTS.size() - 1); // 42 slots (excluding lunch)

        for (Map<String, Object> room : roomsList) {
            String id = room.get("id").toString();
            int busy = usage.getOrDefault(id, 0);
            int free = totalAvailableSlots - busy;
            double pct = ((double) busy / totalAvailableSlots) * 100.0;

            Map<String, Object> ut = new LinkedHashMap<>();
            ut.put("roomId", id);
            ut.put("roomName", room.get("name").toString());
            ut.put("occupancyPercent", Math.round(pct * 10.0) / 10.0);
            ut.put("freeHours", free);
            ut.put("utilization", Math.round(((double) busy / totalAvailableSlots) * 100.0) / 100.0);
            utilization.add(ut);
        }

        return utilization;
    }

    // Local file loading helpers
    private static List<Map<String, Object>> loadDataList(Path file) throws IOException {
        if (!Files.exists(file)) return List.of();
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return parseList(content);
    }

    private static List<Map<String, Object>> parseList(String json) {
        Object val = JsonUtil.parse(json);
        if (val instanceof List) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Object item : (List<?>) val) {
                if (item instanceof Map) {
                    list.add((Map<String, Object>) item);
                }
            }
            return list;
        }
        return List.of();
    }

    // AI auto-fix optimization (Smart Fix)
    @SuppressWarnings("unchecked")
    public static String autoFixTimetable(String requestBody) throws Exception {
        Map<String, Object> body = (Map<String, Object>) JsonUtil.parse(requestBody);
        List<Map<String, Object>> currentTimetable = (List<Map<String, Object>>) body.get("currentTimetable");
        String dept = (String) body.get("dept");
        String semester = body.get("semester").toString();
        
        List<Map<String, Object>> courses = loadDataList(COURSES_FILE);
        List<Map<String, Object>> faculty = loadDataList(FACULTY_FILE);
        List<Map<String, Object>> rooms = loadDataList(ROOMS_FILE);

        // Filter courses to only include those for the target semester and department
        List<Map<String, Object>> filteredCourses = new ArrayList<>();
        for (Map<String, Object> c : courses) {
            String cDept = (String) c.get("dept");
            String cSem = c.get("semester").toString();
            if (dept.equalsIgnoreCase(cDept) && semester.equals(cSem)) {
                filteredCourses.add(c);
            }
        }

        String apiKey = GroqClient.getApiKey();
        boolean useAI = !apiKey.isEmpty();

        if (useAI) {
            String systemPrompt = "You are an AI Timetable Optimization Engine.\n" +
                    "Your task is to take the current timetable and registries, resolve all constraint violations (hard and soft), and return a corrected, optimized timetable.\n" +
                    "Hard Constraints (MUST NOT be violated):\n" +
                    "- No faculty clashes (a teacher cannot teach two sections at the same time).\n" +
                    "- No room clashes (two classes cannot occupy the same room at the same time).\n" +
                    "- No student clashes (a section cannot have two classes at the same time).\n" +
                    "- Respect faculty daily maximum teaching hours.\n" +
                    "- No classes during lunch break (12:00-1:00 PM).\n" +
                    "Soft Constraints (Minimize/Eliminate):\n" +
                    "- Avoid late classes (4:00-5:00 PM).\n" +
                    "- Avoid idle gaps between classes for students and teachers.\n" +
                    "\n" +
                    "Return ONLY a JSON response in the following schema:\n" +
                    "{\n" +
                    "  \"timetable\": [\n" +
                    "     { \"day\": \"Monday\", \"slot\": \"8:00-9:00\", \"course\": \"CS301\", \"faculty\": \"F01\", \"room\": \"R101\", \"section\": \"A\", \"semester\": 5, \"dept\": \"CSE\", \"color\": \"#1a4a8a\" }\n" +
                    "  ]\n" +
                    "}\n" +
                    "Output valid JSON only. Never explain.";

            String userPrompt = "Optimize and fix all violations in this timetable:\n" +
                    "Current Timetable:\n" + JsonUtil.toJson(currentTimetable) + "\n\n" +
                    "Courses Registry:\n" + JsonUtil.toJson(courses) + "\n\n" +
                    "Faculty Registry:\n" + JsonUtil.toJson(faculty) + "\n\n" +
                    "Rooms Registry:\n" + JsonUtil.toJson(rooms) + "\n\n" +
                    "Ensure all courses for department " + dept + " and semester " + semester + " are correctly scheduled without errors.";

            List<Map<String, Object>> optimizedTimetable = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String aiResponse = GroqClient.callGroq(systemPrompt, userPrompt);
                    Map<String, Object> parsed = ResponseParser.parseResponse(aiResponse);
                    optimizedTimetable = (List<Map<String, Object>>) parsed.get("timetable");
                    if (optimizedTimetable != null) {
                        ValidationEngine.ValidationResult val = ValidationEngine.validate(optimizedTimetable, courses, faculty, rooms);
                        if (val.hardCount == 0) {
                            break; // Success!
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Auto-fix attempt " + attempt + " failed: " + e.getMessage());
                }
            }

            if (optimizedTimetable != null) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("timetable", optimizedTimetable);
                ValidationEngine.ValidationResult val = ValidationEngine.validate(optimizedTimetable, courses, faculty, rooms);
                resp.put("hardViolations", val.hardCount);
                resp.put("softViolations", val.softCount);
                resp.put("overallScore", val.overallScore);
                resp.put("conflicts", val.conflicts);
                return JsonUtil.toJson(resp);
            }
        }

        // Fallback to local solver to rebuild cleanly and automatically
        System.out.println("AI Auto-fix failed, was disabled, or had violations. Rebuilding cleanly with local solver...");
        String constraintsJson = "{\"maxPerDay\":5,\"lunchBreak\":true,\"labContiguous\":true,\"avoidSaturday\":false}";
        Map<String, Object> dummyConstraints = (Map<String, Object>) JsonUtil.parse(constraintsJson);
        
        List<Map<String, Object>> resolved = parseList(generateWithLocalSolver(filteredCourses, faculty, rooms, dept, semester, "All", dummyConstraints, "tight"));
        
        // Merge this cleanly generated semester timetable back into the other semesters
        List<Map<String, Object>> existing = loadDataList(TIMETABLE_FILE);
        List<Map<String, Object>> merged = new ArrayList<>();
        
        String targetDeptSem = (dept + "@" + semester).toLowerCase();
        for (Map<String, Object> entry : existing) {
            String entryDept = (String) entry.get("dept");
            String entrySem = entry.get("semester").toString();
            String combo = (entryDept + "@" + entrySem).toLowerCase();
            if (!combo.equals(targetDeptSem)) {
                merged.add(entry);
            }
        }
        merged.addAll(resolved);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("timetable", merged);
        
        ValidationEngine.ValidationResult val = ValidationEngine.validate(merged, courses, faculty, rooms);
        resp.put("hardViolations", val.hardCount);
        resp.put("softViolations", val.softCount);
        resp.put("overallScore", val.overallScore);
        resp.put("conflicts", val.conflicts);
        
        return JsonUtil.toJson(resp);
    }

    private static String getSampleTimetableJson() {
        return "[\n" +
                "  {\"day\":\"Monday\",\"slot\":\"8:00-9:00\",\"course\":\"CS301\",\"faculty\":\"F01\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#1a4a8a\"},\n" +
                "  {\"day\":\"Monday\",\"slot\":\"9:00-10:00\",\"course\":\"CS302\",\"faculty\":\"F04\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#c0392b\"},\n" +
                "  {\"day\":\"Monday\",\"slot\":\"10:00-11:00\",\"course\":\"CS401\",\"faculty\":\"F01\",\"room\":\"R201\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#1a7a46\"},\n" +
                "  {\"day\":\"Monday\",\"slot\":\"2:00-3:00\",\"course\":\"CS-LAB2\",\"faculty\":\"F04\",\"room\":\"LAB1\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#b7620a\"},\n" +
                "  {\"day\":\"Monday\",\"slot\":\"3:00-4:00\",\"course\":\"CS-LAB2\",\"faculty\":\"F04\",\"room\":\"LAB1\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#b7620a\"},\n" +
                "  {\"day\":\"Tuesday\",\"slot\":\"8:00-9:00\",\"course\":\"CS402\",\"faculty\":\"F04\",\"room\":\"R202\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#5a2d82\"},\n" +
                "  {\"day\":\"Tuesday\",\"slot\":\"9:00-10:00\",\"course\":\"CS501\",\"faculty\":\"F02\",\"room\":\"R201\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#0d6e7a\"},\n" +
                "  {\"day\":\"Tuesday\",\"slot\":\"10:00-11:00\",\"course\":\"CS301\",\"faculty\":\"F01\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#1a4a8a\"},\n" +
                "  {\"day\":\"Wednesday\",\"slot\":\"8:00-9:00\",\"course\":\"CS302\",\"faculty\":\"F04\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#c0392b\"},\n" +
                "  {\"day\":\"Wednesday\",\"slot\":\"11:00-12:00\",\"course\":\"CS402\",\"faculty\":\"F04\",\"room\":\"R202\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#5a2d82\"},\n" +
                "  {\"day\":\"Wednesday\",\"slot\":\"2:00-3:00\",\"course\":\"CS-LAB2\",\"faculty\":\"F04\",\"room\":\"LAB2\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#b7620a\"},\n" +
                "  {\"day\":\"Wednesday\",\"slot\":\"3:00-4:00\",\"course\":\"CS-LAB2\",\"faculty\":\"F04\",\"room\":\"LAB2\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#b7620a\"},\n" +
                "  {\"day\":\"Thursday\",\"slot\":\"8:00-9:00\",\"course\":\"CS501\",\"faculty\":\"F02\",\"room\":\"R201\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#0d6e7a\"},\n" +
                "  {\"day\":\"Thursday\",\"slot\":\"9:00-10:00\",\"course\":\"CS401\",\"faculty\":\"F01\",\"room\":\"R201\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#1a7a46\"},\n" +
                "  {\"day\":\"Thursday\",\"slot\":\"10:00-11:00\",\"course\":\"CS302\",\"faculty\":\"F04\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#c0392b\"},\n" +
                "  {\"day\":\"Friday\",\"slot\":\"8:00-9:00\",\"course\":\"CS301\",\"faculty\":\"F01\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#1a4a8a\"},\n" +
                "  {\"day\":\"Friday\",\"slot\":\"9:00-10:00\",\"course\":\"CS402\",\"faculty\":\"F04\",\"room\":\"R202\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#5a2d82\"},\n" +
                "  {\"day\":\"Friday\",\"slot\":\"11:00-12:00\",\"course\":\"CS501\",\"faculty\":\"F02\",\"room\":\"R201\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#0d6e7a\"},\n" +
                "  {\"day\":\"Saturday\",\"slot\":\"8:00-9:00\",\"course\":\"CS302\",\"faculty\":\"F04\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#c0392b\"},\n" +
                "  {\"day\":\"Saturday\",\"slot\":\"9:00-10:00\",\"course\":\"CS301\",\"faculty\":\"F01\",\"room\":\"R101\",\"section\":\"A\",\"semester\":5,\"dept\":\"CSE\",\"color\":\"#1a4a8a\"}\n" +
                "]";
    }
}
