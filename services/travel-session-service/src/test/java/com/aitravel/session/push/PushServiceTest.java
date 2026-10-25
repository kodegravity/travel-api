package com.aitravel.session.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aitravel.session.support.MutableClock;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class PushServiceTest {

    private static final String TRAVELER = "okta-subject";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/abc123";

    private final MutableClock clock = new MutableClock();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RecordingGateway gateway = new RecordingGateway();
    private final KeyPair vapidKeys = p256();
    private final PushProperties properties = new PushProperties(true, B64.encodeToString(
            WebPushEncryption.uncompressed((ECPublicKey) vapidKeys.getPublic())),
            B64.encodeToString(WebPushEncryption.fixed32(((ECPrivateKey) vapidKeys.getPrivate()).getS())),
            "mailto:ops@example.com", Duration.ofHours(4), 2, Duration.ofSeconds(5));
    private final PushService service = newService(properties);

    @Test
    void acceptsOnlyKnownPushServices() {
        for (String endpoint : List.of("http://fcm.googleapis.com/x", "https://evil.example.com/x",
                "https://fcm.googleapis.com.evil.example/x", "https://fcm.googleapis.com:8443/x",
                "https://user@fcm.googleapis.com/x", "not a url")) {
            assertThatThrownBy(() -> service.subscribe(TRAVELER, request(endpoint, browserKey(), auth())))
                    .as(endpoint).isInstanceOf(InvalidSubscriptionException.class);
        }
        assertThat(service.subscribe(TRAVELER, request("https://updates.push.services.mozilla.com/wpush/v2/x",
                browserKey(), auth())).subscriptions()).isEqualTo(1);
        assertThat(service.subscribe(TRAVELER, request("https://web.push.apple.com/QGx", browserKey(), auth()))
                .subscriptions()).isEqualTo(2);
    }

    @Test
    void rejectsKeysOfTheWrongLength() {
        assertThatThrownBy(() -> service.subscribe(TRAVELER, request(FCM, B64.encodeToString(new byte[33]), auth())))
                .isInstanceOf(InvalidSubscriptionException.class).hasMessageContaining("p256dh");
        assertThatThrownBy(() -> service.subscribe(TRAVELER, request(FCM, browserKey(), B64.encodeToString(new byte[8]))))
                .isInstanceOf(InvalidSubscriptionException.class).hasMessageContaining("auth");
    }

    @Test
    void resubscribingReplacesAndTheOldestBrowserIsDroppedPastTheLimit() {
        service.subscribe(TRAVELER, request(FCM + "1", browserKey(), auth()));
        service.subscribe(TRAVELER, request(FCM + "2", browserKey(), auth()));
        service.subscribe(TRAVELER, request(FCM + "2", browserKey(), auth()));
        assertThat(service.config(TRAVELER).subscriptions()).isEqualTo(2);

        service.subscribe(TRAVELER, request(FCM + "3", browserKey(), auth()));
        service.notify(TRAVELER, message());
        assertThat(gateway.endpoints).containsExactlyInAnyOrder(URI.create(FCM + "3"), URI.create(FCM + "2"));

        assertThat(service.unsubscribe(TRAVELER, FCM + "3").subscriptions()).isEqualTo(1);
    }

    @Test
    void sendsAnEncryptedMessageSignedForThePushServiceOrigin() throws Exception {
        service.subscribe(TRAVELER, request(FCM, browserKey(), auth()));

        assertThat(service.notify(TRAVELER, message())).isEqualTo(1);

        Map<String, String> headers = gateway.headers.getFirst();
        assertThat(headers).containsEntry("Content-Encoding", "aes128gcm").containsEntry("TTL", "14400")
                .containsEntry("Topic", "travel-session-updates");
        String authorization = headers.get("Authorization");
        assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + properties.vapidPublicKey());
        String[] jwt = authorization.substring("vapid t=".length(), authorization.indexOf(',')).split("\\.");
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(jwt[1]));
        assertThat(claims.get("aud").asString()).isEqualTo("https://fcm.googleapis.com");
        assertThat(claims.get("sub").asString()).isEqualTo("mailto:ops@example.com");
        assertThat(claims.get("exp").asLong()).isEqualTo(clock.instant().plus(Vapid.TOKEN_LIFETIME).getEpochSecond());
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(vapidKeys.getPublic());
        verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
        // Header (86 bytes) + JSON + delimiter + GCM tag: the payload is encrypted, never sent in the clear.
        assertThat(new String(gateway.bodies.getFirst(), StandardCharsets.ISO_8859_1)).doesNotContain("new places");
    }

    @Test
    void anExpiredSubscriptionIsRemoved() {
        service.subscribe(TRAVELER, request(FCM, browserKey(), auth()));
        gateway.status = 410;

        assertThat(service.notify(TRAVELER, message())).isZero();
        assertThat(service.config(TRAVELER).subscriptions()).isZero();
    }

    @Test
    void aFailingPushServiceKeepsTheSubscription() {
        service.subscribe(TRAVELER, request(FCM, browserKey(), auth()));
        gateway.status = 503;

        assertThat(service.notify(TRAVELER, message())).isZero();
        assertThat(service.config(TRAVELER).subscriptions()).isEqualTo(1);
    }

    @Test
    void offMeansNoKeyNoSubscriptionsAndNothingSent() {
        PushService off = newService(new PushProperties(false, properties.vapidPublicKey(),
                properties.vapidPrivateKey(), "mailto:x@example.com", Duration.ofHours(1), 5, Duration.ofSeconds(5)));

        assertThat(off.config(TRAVELER)).isEqualTo(new PushConfigResponse(false, null, 0));
        assertThatThrownBy(() -> off.subscribe(TRAVELER, request(FCM, browserKey(), auth())))
                .isInstanceOf(PushUnavailableException.class);
        assertThat(off.notify(TRAVELER, message())).isZero();
        assertThat(gateway.endpoints).isEmpty();
    }

    @Test
    void topicsFitTheThirtyTwoCharacterLimit() {
        assertThat(PushService.topic("live-event-Z7r9jZ1A7j-_Ticketmaster.long-id")).hasSize(32).matches("[A-Za-z0-9_-]+");
    }

    private PushService newService(PushProperties props) {
        Vapid vapid = null;
        if (props.enabled()) {
            try {
                vapid = new Vapid(props.vapidPublicKey(), props.vapidPrivateKey(), props.subject());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
        return new PushService(props, vapid, new InMemoryPushSubscriptionStore(), gateway, mapper, clock);
    }

    private static PushMessage message() {
        return new PushMessage("3 new places nearby", "Open ROAM to see them.", "travel-session-updates", "/travel-mode");
    }

    private static SubscribeRequest request(String endpoint, String p256dh, String auth) {
        return new SubscribeRequest(endpoint, new SubscribeRequest.Keys(p256dh, auth));
    }

    private static String browserKey() {
        return B64.encodeToString(WebPushEncryption.uncompressed((ECPublicKey) p256().getPublic()));
    }

    private static String auth() {
        return B64.encodeToString(new byte[16]);
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

    private static final class RecordingGateway implements PushGateway {
        int status = 201;
        final List<URI> endpoints = new ArrayList<>();
        final List<Map<String, String>> headers = new ArrayList<>();
        final List<byte[]> bodies = new ArrayList<>();

        @Override
        public int post(URI endpoint, Map<String, String> requestHeaders, byte[] body) {
            endpoints.add(endpoint);
            headers.add(requestHeaders);
            bodies.add(body);
            return status;
        }
    }
}
