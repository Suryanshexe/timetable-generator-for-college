import java.util.*;

public class JsonUtil {

    public static Object parse(String json) {
        if (json == null) return null;
        json = json.trim();
        if (json.isEmpty()) return null;
        int[] index = new int[]{0};
        return parseValue(json, index);
    }

    private static Object parseValue(String json, int[] index) {
        skipWhitespace(json, index);
        if (index[0] >= json.length()) return null;

        char c = json.charAt(index[0]);
        if (c == '{') {
            return parseObject(json, index);
        } else if (c == '[') {
            return parseArray(json, index);
        } else if (c == '"') {
            return parseString(json, index);
        } else if (c == 't' || c == 'f') {
            return parseBoolean(json, index);
        } else if (c == 'n') {
            return parseNull(json, index);
        } else if (Character.isDigit(c) || c == '-') {
            return parseNumber(json, index);
        } else {
            throw new RuntimeException("Unexpected character '" + c + "' at position " + index[0]);
        }
    }

    private static Map<String, Object> parseObject(String json, int[] index) {
        Map<String, Object> map = new LinkedHashMap<>();
        index[0]++; // Consume '{'
        skipWhitespace(json, index);

        if (index[0] >= json.length()) throw new RuntimeException("Unterminated object");
        if (json.charAt(index[0]) == '}') {
            index[0]++;
            return map;
        }

        while (true) {
            skipWhitespace(json, index);
            if (json.charAt(index[0]) != '"') {
                throw new RuntimeException("Expected string key in object at " + index[0]);
            }
            String key = parseString(json, index);
            skipWhitespace(json, index);
            if (index[0] >= json.length() || json.charAt(index[0]) != ':') {
                throw new RuntimeException("Expected ':' at " + index[0]);
            }
            index[0]++; // Consume ':'
            Object val = parseValue(json, index);
            map.put(key, val);

            skipWhitespace(json, index);
            if (index[0] >= json.length()) throw new RuntimeException("Unterminated object");
            char c = json.charAt(index[0]);
            if (c == '}') {
                index[0]++;
                break;
            } else if (c == ',') {
                index[0]++;
            } else {
                throw new RuntimeException("Expected ',' or '}' in object at " + index[0]);
            }
        }
        return map;
    }

    private static List<Object> parseArray(String json, int[] index) {
        List<Object> list = new ArrayList<>();
        index[0]++; // Consume '['
        skipWhitespace(json, index);

        if (index[0] >= json.length()) throw new RuntimeException("Unterminated array");
        if (json.charAt(index[0]) == ']') {
            index[0]++;
            return list;
        }

        while (true) {
            Object val = parseValue(json, index);
            list.add(val);

            skipWhitespace(json, index);
            if (index[0] >= json.length()) throw new RuntimeException("Unterminated array");
            char c = json.charAt(index[0]);
            if (c == ']') {
                index[0]++;
                break;
            } else if (c == ',') {
                index[0]++;
            } else {
                throw new RuntimeException("Expected ',' or ']' in array at " + index[0]);
            }
        }
        return list;
    }

    private static String parseString(String json, int[] index) {
        index[0]++; // Consume starting quote
        StringBuilder sb = new StringBuilder();
        while (index[0] < json.length()) {
            char c = json.charAt(index[0]);
            if (c == '"') {
                index[0]++; // Consume ending quote
                return sb.toString();
            } else if (c == '\\') {
                index[0]++;
                if (index[0] >= json.length()) throw new RuntimeException("Unterminated escape sequence");
                char next = json.charAt(index[0]);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (index[0] + 4 >= json.length()) throw new RuntimeException("Invalid unicode escape");
                        String hex = json.substring(index[0] + 1, index[0] + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        index[0] += 4;
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
            index[0]++;
        }
        throw new RuntimeException("Unterminated string");
    }

    private static Boolean parseBoolean(String json, int[] index) {
        if (json.startsWith("true", index[0])) {
            index[0] += 4;
            return Boolean.TRUE;
        } else if (json.startsWith("false", index[0])) {
            index[0] += 5;
            return Boolean.FALSE;
        }
        throw new RuntimeException("Expected boolean at " + index[0]);
    }

    private static Object parseNull(String json, int[] index) {
        if (json.startsWith("null", index[0])) {
            index[0] += 4;
            return null;
        }
        throw new RuntimeException("Expected null at " + index[0]);
    }

    private static Object parseNumber(String json, int[] index) {
        int start = index[0];
        boolean isDouble = false;
        while (index[0] < json.length()) {
            char c = json.charAt(index[0]);
            if (c == '.' || c == 'e' || c == 'E') {
                isDouble = true;
            }
            if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                index[0]++;
            } else {
                break;
            }
        }
        String numStr = json.substring(start, index[0]);
        if (isDouble) {
            return Double.parseDouble(numStr);
        } else {
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }
    }

    private static void skipWhitespace(String json, int[] index) {
        while (index[0] < json.length() && Character.isWhitespace(json.charAt(index[0]))) {
            index[0]++;
        }
    }

    public static String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + escape((String) obj) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            StringBuilder sb = new StringBuilder("{");
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(entry.getKey().toString())).append("\":")
                        .append(toJson(entry.getValue()));
                i++;
            }
            sb.append("}");
            return sb.toString();
        }
        return "\"" + escape(obj.toString()) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < ' ') {
                        String t = "000" + Integer.toHexString(ch);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }
}
