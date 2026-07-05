import java.util.Map;

public class ResponseParser {

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseResponse(String rawJson) {
        Object parsed = JsonUtil.parse(rawJson);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        throw new RuntimeException("Invalid response format: Expected JSON Object");
    }
}
