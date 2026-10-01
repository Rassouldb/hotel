package sn.niirma.shared.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Attribue un identifiant de corrélation à chaque requête : repris de l'en-tête {@code X-Request-Id}
 * s'il est valide, généré sinon. Il est placé dans le MDC (logs) et renvoyé dans {@code X-Trace-Id}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_HEADER = "X-Request-Id";
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(REQUEST_HEADER);
        String traceId = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString().replace("-", "");
        MDC.put(TraceContext.MDC_KEY, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.MDC_KEY);
        }
    }
}
