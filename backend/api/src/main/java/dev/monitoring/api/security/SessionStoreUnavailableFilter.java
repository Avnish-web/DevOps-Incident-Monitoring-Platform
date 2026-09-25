package dev.monitoring.api.security;

import dev.monitoring.api.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wraps the session filter: if Redis (the session store) is unreachable, answer with a
 * Problem Details 503 and Retry-After instead of letting Tomcat render an HTML 500 page.
 * Runs after the request-id filter and before Spring Session's filter.
 */
@Component
@Order(SessionRepositoryFilter.DEFAULT_ORDER - 1)
public class SessionStoreUnavailableFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionStoreUnavailableFilter.class);

    private final ProblemResponses problems;

    public SessionStoreUnavailableFilter(ProblemResponses problems) {
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (DataAccessException e) {
            // Redis failures surface as DataAccessException (connection failure, timeout).
            if (response.isCommitted()) {
                throw e;
            }
            log.warn("Session store unavailable: {}", e.toString());
            response.reset(); // also clears headers set earlier in the chain
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                response.setHeader(RequestIdFilter.HEADER, requestId);
            }
            response.setHeader("Retry-After", "5");
            problems.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    "The service is temporarily unavailable. Please retry shortly.");
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
