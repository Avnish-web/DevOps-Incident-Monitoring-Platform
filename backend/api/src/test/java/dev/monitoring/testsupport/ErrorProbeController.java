package dev.monitoring.testsupport;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints that trigger error paths. Lives outside the application's
 * component-scan package so it is only registered where a test imports it explicitly.
 */
@RestController
public class ErrorProbeController {

    public record ProbeRequest(@NotBlank String name, @Min(30) int intervalSeconds) {
    }

    @PostMapping("/test/validate")
    public ProbeRequest validate(@Valid @RequestBody ProbeRequest request) {
        return request;
    }

    @GetMapping("/test/boom")
    public String boom() {
        throw new IllegalStateException("secret-internal-detail");
    }
}
