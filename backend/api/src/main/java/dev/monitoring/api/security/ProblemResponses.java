package dev.monitoring.api.security;

import dev.monitoring.api.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401/403 responses from the security filter chain, in the same RFC 9457 format as every
 * other API error (these are produced before requests reach Spring MVC).
 */
@Component
public class ProblemResponses {

    private final JsonMapper json;

    public ProblemResponses(JsonMapper json) {
        this.json = json;
    }

    public AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(request, response, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "Authentication is required");
    }

    public AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(request, response, HttpStatus.FORBIDDEN,
                "Forbidden", "You do not have permission for this action, or the CSRF token "
                        + "is missing or invalid");
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(body));
    }
}
