import java.util.List;
import java.util.Map;

public interface ConstraintValidator {
    void validate(
        List<Map<String, Object>> timetable,
        List<Map<String, Object>> courses,
        List<Map<String, Object>> faculty,
        List<Map<String, Object>> rooms,
        ValidationEngine.ValidationResult result
    );
}
