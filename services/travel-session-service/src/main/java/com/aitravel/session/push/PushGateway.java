package com.aitravel.session.push;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** One HTTP POST to a push service. Separate so tests can stand in for the push services. */
public interface PushGateway {

    /** @return the push service's HTTP status */
    int post(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;
}
