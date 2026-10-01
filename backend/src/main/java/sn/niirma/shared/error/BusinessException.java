package sn.niirma.shared.error;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Erreur métier attendue, traduite en réponse au format standard (§34.2) avec son code stable.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<Map<String, Object>> details;

    public BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public BusinessException(HttpStatus status, String code, String message, List<Map<String, Object>> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    public static BusinessException unprocessable(String code, String message) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }
}
