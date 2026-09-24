package dev.monitoring.worker.config;

import dev.monitoring.common.crypto.SecretCipher;
import dev.monitoring.worker.alert.AlertHttpClient;
import dev.monitoring.worker.check.GuardedDnsResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AlertingConfig {

    /** Same key as the API; fails startup with a clear message if missing or malformed. */
    @Bean
    SecretCipher secretCipher(@Value("${monitoring.alerting.encryption-key:}") String key) {
        return SecretCipher.fromBase64(key);
    }

    @Bean(destroyMethod = "close")
    AlertHttpClient alertHttpClient(GuardedDnsResolver guardedDnsResolver,
                                    HttpCheckProperties http) {
        return new AlertHttpClient(guardedDnsResolver, http.userAgent());
    }
}
