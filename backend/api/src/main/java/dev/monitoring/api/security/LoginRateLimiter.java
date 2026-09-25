package dev.monitoring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Limits failed logins per account and per client IP (brute force / credential stuffing),
 * using Redis counters shared by all API replicas. Keys hold a hash of the e-mail address,
 * never the address itself.
 */
@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redis;
    private final int maxFailuresPerAccount;
    private final int maxFailuresPerIp;
    private final Duration window;

    public LoginRateLimiter(StringRedisTemplate redis,
                            @Value("${monitoring.auth.max-failures-per-account:5}") int maxFailuresPerAccount,
                            @Value("${monitoring.auth.max-failures-per-ip:30}") int maxFailuresPerIp,
                            @Value("${monitoring.auth.lockout-window:15m}") Duration window) {
        this.redis = redis;
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.maxFailuresPerIp = maxFailuresPerIp;
        this.window = window;
    }

    /** @return how long the caller must wait, if currently blocked */
    public Optional<Duration> blockedFor(String email, String ip) {
        for (String[] entry : new String[][] {
                {accountKey(email), Integer.toString(maxFailuresPerAccount)},
                {ipKey(ip), Integer.toString(maxFailuresPerIp)}}) {
            String count = redis.opsForValue().get(entry[0]);
            if (count != null && Integer.parseInt(count) >= Integer.parseInt(entry[1])) {
                Long ttl = redis.getExpire(entry[0]);
                return Optional.of(ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : window);
            }
        }
        return Optional.empty();
    }

    public void recordFailure(String email, String ip) {
        increment(accountKey(email));
        increment(ipKey(ip));
    }

    public void reset(String email) {
        redis.delete(accountKey(email));
    }

    private void increment(String key) {
        Long value = redis.opsForValue().increment(key);
        if (value != null && value == 1) {
            redis.expire(key, window);
        }
    }

    private static String accountKey(String email) {
        return "login:fail:account:" + sha256(email);
    }

    private static String ipKey(String ip) {
        return "login:fail:ip:" + sha256(ip == null ? "unknown" : ip);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
