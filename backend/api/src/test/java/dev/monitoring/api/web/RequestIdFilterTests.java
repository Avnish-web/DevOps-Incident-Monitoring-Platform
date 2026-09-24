package dev.monitoring.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestIdFilterTests {

    @Test
    void keepsWellFormedId() {
        assertThat(RequestIdFilter.resolveRequestId("abc-123_X.y")).isEqualTo("abc-123_X.y");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"line\nbreak", "has space", "a\"quote", "{json}",
            "0123456789012345678901234567890123456789012345678901234567890123456789"})
    void replacesMissingOrUnsafeId(String candidate) {
        String resolved = RequestIdFilter.resolveRequestId(candidate);
        assertThat(resolved).isNotEqualTo(candidate).hasSize(36);
    }
}
