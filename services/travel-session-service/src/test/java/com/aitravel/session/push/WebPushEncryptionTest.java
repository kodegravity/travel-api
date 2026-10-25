package com.aitravel.session.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Against the worked example in RFC 8291, appendix A, so the output is what browsers decrypt. */
class WebPushEncryptionTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();
    private static final byte[] PLAINTEXT = B64.decode("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24");
    private static final byte[] SENDER_PUBLIC = B64.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
    private static final byte[] SENDER_PRIVATE = B64.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
    private static final byte[] BROWSER_PUBLIC = B64.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
    private static final byte[] BROWSER_PRIVATE = B64.decode("q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94");
    private static final byte[] SALT = B64.decode("DGv6ra1nlYgDCS1FRnbzlw");
    private static final byte[] AUTH = B64.decode("BTBZMqHH6r4Tts7J_aSIgg");
    /** The RFC's message: an 86-byte header, then the ciphertext of a padded record. */
    private static final byte[] RFC_MESSAGE = B64.decode("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLhWoBl4Tsx6Fj_I-H5PYAcsKW-hUbXm0UNoMH-Ao0dAhxR9aNTeEOmJdkUAE5Xgw3tDDOLw89J");
    private static final int HEADER = 86;

    @Test
    void derivesTheSameKeysAsTheRfcExample() throws Exception {
        byte[] body = encryptLikeTheRfc();

        assertThat(Arrays.copyOf(body, HEADER)).isEqualTo(Arrays.copyOf(RFC_MESSAGE, HEADER));
        // The RFC pads its record and this sender does not, so compare the first two AES blocks: equal only when the
        // content key and nonce are the same.
        assertThat(Arrays.copyOfRange(body, HEADER, HEADER + 32)).isEqualTo(Arrays.copyOfRange(RFC_MESSAGE, HEADER, HEADER + 32));
    }

    @Test
    void theBrowserCanDecryptIt() throws Exception {
        byte[] body = WebPushEncryption.encrypt(PLAINTEXT, BROWSER_PUBLIC, AUTH);

        assertThat(decryptAsBrowser(body)).isEqualTo(WebPushEncryption.concat(PLAINTEXT, new byte[] {2}));
    }

    private static byte[] encryptLikeTheRfc() throws Exception {
        PrivateKey privateKey = privateKey(SENDER_PRIVATE);
        return WebPushEncryption.encrypt(PLAINTEXT, BROWSER_PUBLIC, AUTH,
                new KeyPair(WebPushEncryption.publicKey(SENDER_PUBLIC), privateKey), SALT);
    }

    /** The receiving side of RFC 8291, written out separately from the sender. */
    private static byte[] decryptAsBrowser(byte[] body) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16);
        int keyIdLength = body[20];
        byte[] senderPublic = Arrays.copyOfRange(body, 21, 21 + keyIdLength);
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey(BROWSER_PRIVATE));
        agreement.doPhase(WebPushEncryption.publicKey(senderPublic), true);
        byte[] ikm = expand(hmac(AUTH, agreement.generateSecret()), WebPushEncryption.concat(
                "WebPush: info\0".getBytes(StandardCharsets.US_ASCII), BROWSER_PUBLIC, senderPublic), 32);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(Arrays.copyOfRange(body, 21 + keyIdLength, body.length));
    }

    private static byte[] expand(byte[] prk, byte[] info, int length) throws Exception {
        return Arrays.copyOf(hmac(prk, WebPushEncryption.concat(info, new byte[] {1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static PrivateKey privateKey(byte[] scalar) throws Exception {
        return KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), WebPushEncryption.P256));
    }
}
