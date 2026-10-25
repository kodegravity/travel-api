package com.aitravel.session.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Posts to push services with the JDK HTTP client. Redirects are not followed. */
public class HttpPushGateway implements PushGateway {

    private final HttpClient client;
    private final Duration timeout;

    public HttpPushGateway(Duration timeout) {
        this.client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.timeout = timeout;
    }

    @Override
    public int post(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(request::header);
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
