package sn.niirma.shared.error;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Format standard des erreurs de l'API (cahier des charges §34.2).
 */
public record ApiError(boolean success, Body error, Instant timestamp, String traceId) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Body(String code, String message, List<Map<String, Object>> details) {
    }

    public static ApiError of(String code, String message, List<Map<String, Object>> details,
                              Instant timestamp, String traceId) {
        return new ApiError(false, new Body(code, message, details), timestamp, traceId);
    }
}
