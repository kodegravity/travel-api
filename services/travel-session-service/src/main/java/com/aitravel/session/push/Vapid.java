package com.aitravel.session.push;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * The application server's identity for Web Push (VAPID, RFC 8292): an ES256-signed JWT per push service origin.
 * The key pair is generated once (see docs/WEB_PUSH.md); browsers bind each subscription to its public key.
 */
final class Vapid {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    /** Push services reject tokens valid for more than 24 hours. */
    static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private final String publicKey;
    private final PrivateKey privateKey;
    private final String subject;

    /**
     * @param publicKey  base64url, 65-byte uncompressed P-256 point
     * @param privateKey base64url, 32-byte private scalar
     * @param subject    contact for push services, {@code mailto:} or {@code https:}
     */
    Vapid(String publicKey, String privateKey, String subject) throws GeneralSecurityException {
        byte[] scalar = Base64.getUrlDecoder().decode(privateKey.strip());
        this.privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), WebPushEncryption.P256));
        ECPublicKey parsed = WebPushEncryption.publicKey(Base64.getUrlDecoder().decode(publicKey.strip()));
        this.publicKey = B64.encodeToString(WebPushEncryption.uncompressed(parsed));
        this.subject = subject;
    }

    String publicKey() {
        return publicKey;
    }

    /** The {@code Authorization} header value for a push to {@code endpoint}. */
    String authorization(URI endpoint, Instant now) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":"
                + now.plus(TOKEN_LIFETIME).getEpochSecond() + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        // JWS wants the raw 64-byte r || s signature, not DER.
        Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(privateKey);
        signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + header + "." + claims + "." + B64.encodeToString(signer.sign()) + ", k=" + publicKey;
    }
}
