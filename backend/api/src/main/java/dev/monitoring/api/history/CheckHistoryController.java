package dev.monitoring.api.history;

import dev.monitoring.api.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only check history and statistics for a monitor. */
@RestController
@RequestMapping(path = "/api/v1/monitors/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
public class CheckHistoryController {

    private final CheckHistoryService service;

    public CheckHistoryController(CheckHistoryService service) {
        this.service = service;
    }

    /** Raw results, newest first. */
    @GetMapping("/checks")
    public PageResponse<CheckResultResponse> checks(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "checkedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        return service.checks(id, PageRequest.of(page, size, newestFirst));
    }

    @GetMapping("/stats")
    public MonitorStatsResponse stats(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "24h")
            @Pattern(regexp = "^(1h|24h|7d|30d)$", message = "must be one of 1h, 24h, 7d, 30d")
            String range) {
        return service.stats(id, StatsRange.fromCode(range));
    }
}
