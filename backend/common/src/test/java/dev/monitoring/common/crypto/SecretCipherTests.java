package dev.monitoring.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTests {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY;

    static {
        byte[] other = new byte[32];
        other[0] = 1;
        OTHER_KEY = Base64.getEncoder().encodeToString(other);
    }

    private final SecretCipher cipher = SecretCipher.fromBase64(KEY);

    @Test
    void roundTripsAndUsesFreshIvEachTime() {
        String url = "https://hooks.slack.com/services/T000/B000/XXXX";

        String a = cipher.encrypt(url);
        String b = cipher.encrypt(url);

        assertThat(a).startsWith("v1:").doesNotContain("hooks.slack.com");
        assertThat(a).isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo(url);
        assertThat(cipher.decrypt(b)).isEqualTo(url);
    }

    @Test
    void rejectsTamperedCiphertext() {
        String token = cipher.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(token.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTokenFromAnotherKey() {
        String token = SecretCipher.fromBase64(OTHER_KEY).encrypt("secret");

        assertThatThrownBy(() -> cipher.decrypt(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingOrInvalidKeys() {
        assertThatThrownBy(() -> SecretCipher.fromBase64(""))
                .hasMessageContaining("ALERT_ENCRYPTION_KEY is not configured");
        assertThatThrownBy(() -> SecretCipher.fromBase64("not base64!"))
                .hasMessageContaining("not valid base64");
        assertThatThrownBy(() -> SecretCipher.fromBase64(Base64.getEncoder().encodeToString(new byte[16])))
                .hasMessageContaining("32 bytes");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> cipher.decrypt("plain-text")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA")).isInstanceOf(IllegalArgumentException.class);
    }
}
