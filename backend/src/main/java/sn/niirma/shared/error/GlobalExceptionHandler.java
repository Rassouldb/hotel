package sn.niirma.shared.error;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import sn.niirma.shared.web.TraceContext;

/**
 * Traduit toutes les exceptions en réponses au format standard. Les erreurs techniques ne sont
 * jamais exposées : le client reçoit un message générique et le traceId pour le support.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiError> handleBusiness(BusinessException ex) {
        log.info("Erreur métier {} : {}", ex.getCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, Object>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("field", error.getField());
                    detail.put("issue", error.getDefaultMessage());
                    return detail;
                })
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Les données envoyées sont invalides.", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Le corps de la requête est illisible.", List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "L'en-tête " + ex.getHeaderName() + " est obligatoire.", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Le paramètre " + ex.getName() + " est invalide.", List.of());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "Les données ont été modifiées entre-temps. Rechargez puis réessayez.", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ressource introuvable.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Méthode HTTP non supportée.", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Erreur inattendue", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne est survenue. Communiquez l'identifiant de trace au support.", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           List<Map<String, Object>> details) {
        ApiError body = ApiError.of(code, message, details, Instant.now(clock), TraceContext.currentTraceId());
        return ResponseEntity.status(status).body(body);
    }
}
