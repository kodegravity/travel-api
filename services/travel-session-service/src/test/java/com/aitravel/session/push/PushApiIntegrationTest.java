package com.aitravel.session.push;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aitravel.session.config.JwtTestSupport;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

class PushApiIntegrationTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY})
    @Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
    class Off {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void reportsPushOffAndRefusesSubscriptions() throws Exception {
            mockMvc.perform(get("/api/v1/push/config").with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false))
                    .andExpect(jsonPath("$.publicKey").doesNotExist());

            mockMvc.perform(put("/api/v1/push/subscription").with(jwt()).contentType("application/json")
                            .content(subscription("https://fcm.googleapis.com/fcm/send/x")))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.title").value("Push unavailable"));
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {JwtTestSupport.ISSUER_PROPERTY, JwtTestSupport.AUDIENCE_PROPERTY,
            "travel.push.enabled=true"})
    @Import(JwtTestSupport.DefaultJwtMockMvcConfig.class)
    class On {

        @DynamicPropertySource
        static void vapidKeys(DynamicPropertyRegistry registry) {
            KeyPair keys = p256();
            registry.add("travel.push.vapid-public-key",
                    () -> B64.encodeToString(WebPushEncryption.uncompressed((ECPublicKey) keys.getPublic())));
            registry.add("travel.push.vapid-private-key",
                    () -> B64.encodeToString(WebPushEncryption.fixed32(((ECPrivateKey) keys.getPrivate()).getS())));
        }

        @Autowired
        private MockMvc mockMvc;

        @Test
        void subscribesAndUnsubscribesABrowser() throws Exception {
            String subject = UUID.randomUUID().toString();
            mockMvc.perform(get("/api/v1/push/config").with(jwt().jwt(j -> j.subject(subject))))
                    .andExpect(jsonPath("$.enabled").value(true))
                    .andExpect(jsonPath("$.publicKey").isNotEmpty())
                    .andExpect(jsonPath("$.subscriptions").value(0));

            mockMvc.perform(put("/api/v1/push/subscription").with(jwt().jwt(j -> j.subject(subject)))
                            .contentType("application/json")
                            .content(subscription("https://fcm.googleapis.com/fcm/send/x")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").value(1));

            mockMvc.perform(post("/api/v1/push/unsubscribe").with(jwt().jwt(j -> j.subject(subject)))
                            .contentType("application/json")
                            .content("{\"endpoint\": \"https://fcm.googleapis.com/fcm/send/x\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").value(0));
        }

        @Test
        void refusesAnEndpointThatIsNotAPushService() throws Exception {
            mockMvc.perform(put("/api/v1/push/subscription").with(jwt()).contentType("application/json")
                            .content(subscription("https://169.254.169.254/latest/meta-data")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid push subscription"));
        }

        @Test
        void limitsTestNotifications() throws Exception {
            String subject = UUID.randomUUID().toString();
            mockMvc.perform(post("/api/v1/push/test").with(jwt().jwt(j -> j.subject(subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.delivered").value(0));
            mockMvc.perform(post("/api/v1/push/test").with(jwt().jwt(j -> j.subject(subject))))
                    .andExpect(status().isTooManyRequests());
        }
    }

    private static String subscription(String endpoint) {
        String p256dh = B64.encodeToString(WebPushEncryption.uncompressed((ECPublicKey) p256().getPublic()));
        return """
                {"endpoint": "%s", "keys": {"p256dh": "%s", "auth": "%s"}}
                """.formatted(endpoint, p256dh, B64.encodeToString(new byte[16]));
    }

    private static KeyPair p256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
