package dev.monitoring.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts small secrets at rest (alert channel targets, signing keys) with AES-256-GCM.
 *
 * <p>Token format: {@code v1:<base64(12-byte IV || ciphertext || 16-byte tag)>}. GCM
 * authenticates the data, so a modified or foreign token fails to decrypt instead of producing
 * garbage. The version prefix leaves room for key rotation.
 */
public final class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits)");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    /** @param base64Key 32 random bytes, base64-encoded (e.g. {@code openssl rand -base64 32}) */
    public static SecretCipher fromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("ALERT_ENCRYPTION_KEY is not configured. Generate one "
                    + "with: openssl rand -base64 32");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ALERT_ENCRYPTION_KEY is not valid base64", e);
        }
        if (key.length != 32) {
            throw new IllegalStateException("ALERT_ENCRYPTION_KEY must decode to 32 bytes");
        }
        return new SecretCipher(key);
    }

    public String encrypt(String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** @throws IllegalArgumentException if the token is malformed, tampered with or foreign */
    public String decrypt(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported secret format");
        }
        try {
            byte[] data = Base64.getDecoder().decode(token.substring(PREFIX.length()));
            if (data.length <= IV_BYTES) {
                throw new IllegalArgumentException("Secret is truncated");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Secret could not be decrypted", e);
        }
    }
}
