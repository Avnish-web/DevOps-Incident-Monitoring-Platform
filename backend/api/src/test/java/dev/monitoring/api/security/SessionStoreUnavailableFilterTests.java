package dev.monitoring.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.monitoring.api.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class SessionStoreUnavailableFilterTests {

    private final SessionStoreUnavailableFilter filter =
            new SessionStoreUnavailableFilter(new ProblemResponses(JsonMapper.builder().build()));

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void redisOutageBecomesProblemJson503() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "req-42");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/monitors");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setHeader("X-Stale", "cleared");

        filter.doFilter(request, response, (req, res) -> {
            throw new RedisConnectionFailureException("Unable to connect to Redis");
        });

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("req-42");
        assertThat(response.getHeader("X-Stale")).isNull();
        assertThat(response.getContentAsString())
                .contains("\"status\":503")
                .contains("\"requestId\":\"req-42\"")
                .doesNotContain("Unable to connect");
    }

    @Test
    void otherExceptionsPassThrough() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);
    }
}
