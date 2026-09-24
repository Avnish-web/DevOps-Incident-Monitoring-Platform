package dev.monitoring.api.incident;

import dev.monitoring.api.incident.IncidentResponse.IncidentStatus;
import dev.monitoring.api.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only incident history, newest first. */
@RestController
@RequestMapping(path = "/api/v1/incidents", produces = MediaType.APPLICATION_JSON_VALUE)
public class IncidentController {

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) UUID monitorId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by("id"));
        return service.list(status, monitorId, PageRequest.of(page, size, newestFirst));
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
