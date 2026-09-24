package dev.monitoring.api.config;

import dev.monitoring.common.crypto.SecretCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AlertingConfig {

    /** Fails startup with a clear message if ALERT_ENCRYPTION_KEY is missing or malformed. */
    @Bean
    SecretCipher secretCipher(@Value("${monitoring.alerting.encryption-key:}") String key) {
        return SecretCipher.fromBase64(key);
    }
}
