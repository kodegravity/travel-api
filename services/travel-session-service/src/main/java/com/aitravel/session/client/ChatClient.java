package com.aitravel.session.client;

import com.aitravel.session.dto.ChatRequest;
import com.aitravel.session.exception.AttractionProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** "Ask ROAM" replies from the Recommendation Service ({@code POST /internal/v1/chat}); the JSON is forwarded unchanged. */
@Component
public class ChatClient {

    private static final Logger log = LoggerFactory.getLogger(ChatClient.class);
    static final String CHAT_PATH = "/internal/v1/chat";

    private final RestClient restClient;

    public ChatClient(RestClient recommendationServiceRestClient) {
        this.restClient = recommendationServiceRestClient;
    }

    public String replyJson(ChatRequest request) {
        log.info("Requesting a chat reply from the recommendation service");
        try {
            String body = restClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new AttractionProviderException("Recommendation Service returned no body", null);
            }
            return body;
        } catch (RestClientResponseException ex) {
            log.warn("Chat failed with HTTP status {}", ex.getStatusCode().value());
            throw new AttractionProviderException("Recommendation Service failed with HTTP " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Recommendation Service could not be reached ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service could not be reached", ex);
        } catch (RestClientException ex) {
            log.warn("Chat reply could not be read ({})", ex.getClass().getSimpleName());
            throw new AttractionProviderException("Recommendation Service response could not be read", ex);
        }
    }
}
