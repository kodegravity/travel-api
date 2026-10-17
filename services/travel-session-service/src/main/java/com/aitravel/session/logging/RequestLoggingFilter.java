package com.aitravel.session.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a request ID to every HTTP request, puts it in the MDC so every log line carries it, returns it in the
 * {@code X-Request-Id} response header, and writes one access log line per request.
 *
 * <p>Headers, query strings, and request bodies are never logged. Runs before Spring Security so rejected
 * requests are also logged with a request ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        String method = request.getMethod();
        String path = request.getRequestURI();
        boolean accessLogged = !path.startsWith("/actuator");
        long startNanos = System.nanoTime();
        try {
            log.debug("Request received method={} path={}", method, path);
            chain.doFilter(request, response);
        } finally {
            if (accessLogged) {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
                log.info("Request completed method={} path={} status={} durationMs={}",
                        method, path, response.getStatus(), durationMs);
            }
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    /** Reuses a well-formed upstream request ID so one ID follows a request across services. */
    static String resolveRequestId(String incoming) {
        if (incoming != null && VALID_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
