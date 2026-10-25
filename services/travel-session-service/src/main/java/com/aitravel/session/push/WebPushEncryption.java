package com.aitravel.session.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Message encryption for Web Push (RFC 8291, {@code aes128gcm} from RFC 8188), with the JDK's own crypto. Each
 * message gets a fresh sender key pair and salt, and fits in one record.
 */
final class WebPushEncryption {

    static final ECParameterSpec P256 = p256();
    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {
    }

    /** Encrypts {@code plaintext} for the browser whose subscription holds {@code p256dh} and {@code auth}. */
    static byte[] encrypt(byte[] plaintext, byte[] p256dh, byte[] auth) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, p256dh, auth, generator.generateKeyPair(), salt);
    }

    /** The deterministic core, given the sender key pair and salt (the RFC's test vector fixes both). */
    static byte[] encrypt(byte[] plaintext, byte[] p256dh, byte[] auth, KeyPair sender, byte[] salt)
            throws GeneralSecurityException {
        byte[] senderPublic = uncompressed((ECPublicKey) sender.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(sender.getPrivate());
        agreement.doPhase(publicKey(p256dh), true);
        byte[] ecdhSecret = agreement.generateSecret();

        // RFC 8291 section 3.4: mix the auth secret and both public keys into the input keying material.
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), p256dh, senderPublic);
        byte[] ikm = hkdf(auth, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // 0x02 marks the last (and only) record.
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

        // RFC 8188 header: salt, record size, key id length, key id (the sender's public key).
        return ByteBuffer.allocate(16 + 4 + 1 + senderPublic.length + ciphertext.length)
                .put(salt)
                .putInt(RECORD_SIZE)
                .put((byte) senderPublic.length)
                .put(senderPublic)
                .put(ciphertext)
                .array();
    }

    /** A 65-byte uncompressed point (0x04 || X || Y) as a P-256 public key. */
    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("Not an uncompressed P-256 point");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[] {4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    /** HKDF-SHA256 (RFC 5869), for outputs of at most one hash length. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    /** An unsigned coordinate as exactly 32 bytes. */
    static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        }
        byte[] fixed = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, fixed, 32 - copy, copy);
        return fixed;
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        return buffer.array();
    }

    private static ECParameterSpec p256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return ((ECPublicKey) generator.generateKeyPair().getPublic()).getParams();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("P-256 is not available", ex);
        }
    }
}
