package sn.niirma.shared.web;

import org.slf4j.MDC;

/**
 * Accès à l'identifiant de corrélation de la requête courante (placé dans le MDC par {@link TraceIdFilter}).
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String currentTraceId() {
        return MDC.get(MDC_KEY);
    }
}
