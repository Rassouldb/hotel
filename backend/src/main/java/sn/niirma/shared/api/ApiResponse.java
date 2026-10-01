package sn.niirma.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Enveloppe standard des réponses réussies : {@code { "success": true, "data": …, "meta": … }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, PageMeta meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, meta);
    }
}
