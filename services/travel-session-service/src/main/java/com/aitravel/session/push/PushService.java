package com.aitravel.session.push;

import com.aitravel.session.logging.LogIds;
import com.aitravel.session.plan.TravelPlanService;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Web Push: stores each traveler's browser subscriptions and sends them encrypted messages. Only known push services
 * are accepted as endpoints, so a subscription can never make this service call an arbitrary URL.
 */
public class PushService implements PushNotifier {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int MAX_SAVE_ATTEMPTS = 3;
    /** Chrome/Edge/Android (FCM), Firefox (Mozilla autopush), Safari (Apple), and legacy Edge (WNS). */
    private static final Set<String> PUSH_SERVICE_SUFFIXES = Set.of(
            "fcm.googleapis.com", "android.googleapis.com", "push.services.mozilla.com", "push.apple.com",
            "notify.windows.com");
    private static final TypeReference<List<PushSubscription>> SUBSCRIPTIONS = new TypeReference<>() {
    };

    private final PushProperties properties;
    private final Vapid vapid;
    private final PushSubscriptionStore store;
    private final PushGateway gateway;
    private final JsonMapper mapper;
    private final Clock clock;

    /** {@code vapid} is null while push is disabled. */
    PushService(PushProperties properties, Vapid vapid, PushSubscriptionStore store, PushGateway gateway,
                JsonMapper mapper, Clock clock) {
        this.properties = properties;
        this.vapid = vapid;
        this.store = store;
        this.gateway = gateway;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PushConfigResponse config(String subject) {
        if (vapid == null) {
            return new PushConfigResponse(false, null, 0);
        }
        return new PushConfigResponse(true, vapid.publicKey(), subscriptions(TravelPlanService.ownerKey(subject)).size());
    }

    public PushConfigResponse subscribe(String subject, SubscribeRequest request) {
        requireEnabled();
        URI endpoint = pushServiceEndpoint(request.endpoint());
        requireLength(request.keys().p256dh(), 65, "p256dh");
        requireLength(request.keys().auth(), 16, "auth");
        PushSubscription added = new PushSubscription(endpoint.toString(), request.keys().p256dh(),
                request.keys().auth(), clock.instant());
        int count = change(subject, current -> {
            List<PushSubscription> next = new ArrayList<>();
            next.add(added);
            current.stream()
                    .filter(existing -> !existing.endpoint().equals(added.endpoint()))
                    .limit(properties.maxSubscriptions() - 1L)
                    .forEach(next::add);
            return next;
        });
        log.info("Push subscription saved owner={} service={} subscriptions={}", LogIds.subject(subject),
                endpoint.getHost(), count);
        return new PushConfigResponse(true, vapid.publicKey(), count);
    }

    public PushConfigResponse unsubscribe(String subject, String endpoint) {
        requireEnabled();
        int count = change(subject, current -> current.stream()
                .filter(existing -> !existing.endpoint().equals(endpoint))
                .toList());
        log.info("Push subscription removed owner={} subscriptions={}", LogIds.subject(subject), count);
        return new PushConfigResponse(true, vapid.publicKey(), count);
    }

    @Override
    public int notify(String subject, PushMessage message) {
        if (vapid == null) {
            return 0;
        }
        String owner = TravelPlanService.ownerKey(subject);
        List<PushSubscription> subscriptions;
        try {
            subscriptions = subscriptions(owner);
        } catch (RuntimeException ex) {
            log.warn("Push skipped, subscriptions unavailable owner={} cause={}", LogIds.subject(subject),
                    ex.getClass().getSimpleName());
            return 0;
        }
        byte[] payload = mapper.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
        int delivered = 0;
        List<String> gone = new ArrayList<>();
        for (PushSubscription subscription : subscriptions) {
            Outcome outcome = send(subscription, payload, message.tag());
            if (outcome == Outcome.DELIVERED) {
                delivered++;
            } else if (outcome == Outcome.GONE) {
                gone.add(subscription.endpoint());
            }
        }
        if (!gone.isEmpty()) {
            try {
                change(subject, current -> current.stream().filter(s -> !gone.contains(s.endpoint())).toList());
            } catch (RuntimeException ex) {
                log.warn("Expired push subscriptions not removed owner={} cause={}", LogIds.subject(subject),
                        ex.getClass().getSimpleName());
            }
        }
        log.info("Push sent owner={} tag={} delivered={} expired={} subscriptions={}", LogIds.subject(subject),
                message.tag(), delivered, gone.size(), subscriptions.size());
        return delivered;
    }

    private enum Outcome { DELIVERED, GONE, FAILED }

    private Outcome send(PushSubscription subscription, byte[] payload, String tag) {
        URI endpoint = URI.create(subscription.endpoint());
        try {
            byte[] body = WebPushEncryption.encrypt(payload, decode(subscription.p256dh()), decode(subscription.auth()));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", vapid.authorization(endpoint, clock.instant()));
            headers.put("Content-Encoding", "aes128gcm");
            headers.put("Content-Type", "application/octet-stream");
            headers.put("TTL", Long.toString(properties.ttl().toSeconds()));
            headers.put("Urgency", "normal");
            // A newer message with the same topic replaces one the browser has not received yet.
            headers.put("Topic", topic(tag));
            int status = gateway.post(endpoint, headers, body);
            if (status >= 200 && status < 300) {
                return Outcome.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            log.warn("Push service refused message service={} status={}", endpoint.getHost(), status);
            return Outcome.FAILED;
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Keys that cannot be used will never work: drop the subscription.
            log.warn("Push subscription unusable service={} cause={}", endpoint.getHost(), ex.getClass().getSimpleName());
            return Outcome.GONE;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        } catch (Exception ex) {
            log.warn("Push failed service={} cause={}", endpoint.getHost(), ex.getClass().getSimpleName());
            return Outcome.FAILED;
        }
    }

    /** Read, change, and save, starting over when another device saved in between. Returns the new count. */
    private int change(String subject, UnaryOperator<List<PushSubscription>> edit) {
        String owner = TravelPlanService.ownerKey(subject);
        for (int attempt = 1; attempt <= MAX_SAVE_ATTEMPTS; attempt++) {
            Optional<StoredPushSubscriptions> current = store.find(owner);
            long version = current.map(StoredPushSubscriptions::version).orElse(0L);
            List<PushSubscription> next = edit.apply(current.map(this::read).orElse(List.of()));
            if (store.save(owner, version, new StoredPushSubscriptions(version + 1, mapper.writeValueAsString(next),
                    clock.instant()))) {
                return next.size();
            }
        }
        throw new PushUnavailableException("Push subscriptions kept changing");
    }

    private List<PushSubscription> subscriptions(String owner) {
        return store.find(owner).map(this::read).orElse(List.of());
    }

    private List<PushSubscription> read(StoredPushSubscriptions stored) {
        return mapper.readValue(stored.subscriptionsJson(), SUBSCRIPTIONS);
    }

    private void requireEnabled() {
        if (vapid == null) {
            throw new PushUnavailableException("Push notifications are turned off");
        }
    }

    static URI pushServiceEndpoint(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException ex) {
            throw new InvalidSubscriptionException("endpoint is not a URL");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean known = PUSH_SERVICE_SUFFIXES.stream().anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1 || !known) {
            throw new InvalidSubscriptionException("endpoint is not a known push service");
        }
        return uri;
    }

    private static void requireLength(String base64, int bytes, String field) {
        try {
            if (decode(base64).length != bytes) {
                throw new InvalidSubscriptionException(field + " has the wrong length");
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidSubscriptionException(field + " is not base64url");
        }
    }

    private static byte[] decode(String base64) {
        return Base64.getUrlDecoder().decode(base64.replace("=", ""));
    }

    /** RFC 8030 topics are at most 32 base64url characters. */
    static String topic(String tag) {
        String cleaned = tag == null ? "roam" : tag.replaceAll("[^A-Za-z0-9_-]", "-");
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(cleaned.length() - 32);
    }
}
