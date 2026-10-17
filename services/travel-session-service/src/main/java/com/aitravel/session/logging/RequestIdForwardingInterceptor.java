package com.aitravel.session.logging;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.ClientHttpRequestInterceptor;

/** Forwards the current request ID to downstream services so their logs share the same correlation ID. */
public final class RequestIdForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String requestId = MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY);
        if (requestId != null) {
            request.getHeaders().set(RequestLoggingFilter.REQUEST_ID_HEADER, requestId);
        }
        return execution.execute(request, body);
    }
}
