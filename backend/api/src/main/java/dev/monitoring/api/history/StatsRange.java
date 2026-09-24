package dev.monitoring.api.history;

import java.time.Duration;
import java.util.Arrays;

/**
 * Supported time windows for statistics. Each has a bucket size chosen to give roughly
 * 60-120 points, enough for a readable chart without shipping raw results.
 */
public enum StatsRange {
    HOUR("1h", Duration.ofHours(1), Duration.ofMinutes(1)),
    DAY("24h", Duration.ofHours(24), Duration.ofMinutes(15)),
    WEEK("7d", Duration.ofDays(7), Duration.ofHours(2)),
    MONTH("30d", Duration.ofDays(30), Duration.ofHours(6));

    private final String code;
    private final Duration window;
    private final Duration bucket;

    StatsRange(String code, Duration window, Duration bucket) {
        this.code = code;
        this.window = window;
        this.bucket = bucket;
    }

    public String code() {
        return code;
    }

    public Duration window() {
        return window;
    }

    public Duration bucket() {
        return bucket;
    }

    /** Parses "1h", "24h", "7d" or "30d"; anything else is rejected. */
    public static StatsRange fromCode(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "range must be one of 1h, 24h, 7d, 30d"));
    }
}
