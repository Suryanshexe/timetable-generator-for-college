import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class GroqClient {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "llama-3.1-8b-instant";

    public static String getApiKey() {
        // 1. Try system env
        String envKey = System.getenv("GROQ_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }

        // 2. Try loading from workspace root .env file
        try {
            Path envPath = Path.of(".env");
            if (Files.exists(envPath)) {
                for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.startsWith("GROQ_API_KEY=")) {
                        return line.substring("GROQ_API_KEY=".length()).replace("\"", "").replace("'", "").trim();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading .env file: " + e.getMessage());
        }

        return "";
    }

    public static String callGroq(String systemPrompt, String userPrompt) throws Exception {
        String apiKey = getApiKey();
        if (apiKey.isEmpty()) {
            throw new IllegalStateException("GROQ_API_KEY is not set. Please set the GROQ_API_KEY in a .env file or system environment variables.");
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", MODEL_NAME);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        messages.add(systemMessage);

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        String jsonPayload = JsonUtil.toJson(requestBody);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error (" + response.statusCode() + "): " + response.body());
        }

        Map<String, Object> responseMap = (Map<String, Object>) JsonUtil.parse(response.body());
        List<?> choices = (List<?>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Empty choices in Groq response: " + response.body());
        }

        Map<String, Object> firstChoice = (Map<String, Object>) choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        return (String) message.get("content");
    }
}
