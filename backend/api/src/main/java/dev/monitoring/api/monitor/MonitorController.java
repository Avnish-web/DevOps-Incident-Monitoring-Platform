package dev.monitoring.api.monitor;

import dev.monitoring.api.web.PageResponse;
import dev.monitoring.api.web.PreconditionFailedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * CRUD for monitors. Responses carry an {@code ETag} with the monitor's version; clients can
 * send it back in {@code If-Match} on PUT to avoid overwriting someone else's change.
 */
@RestController
@RequestMapping(path = "/api/v1/monitors", produces = MediaType.APPLICATION_JSON_VALUE)
public class MonitorController {

    /** Sortable fields exposed by the API, mapped to entity properties. */
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "name", "name",
            "status", "status",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final MonitorService service;

    public MonitorController(MonitorService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MonitorResponse> create(@Valid @RequestBody MonitorRequest request) {
        MonitorResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).eTag(etag(created)).body(created);
    }

    @GetMapping
    public PageResponse<MonitorResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc")
            @Pattern(regexp = "^(name|status|createdAt|updatedAt)(,(asc|desc))?$",
                    message = "must be one of name, status, createdAt, updatedAt "
                            + "optionally followed by ,asc or ,desc")
            String sort) {
        return service.list(PageRequest.of(page, size, toSort(sort)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MonitorResponse> get(@PathVariable UUID id) {
        MonitorResponse monitor = service.get(id);
        return ResponseEntity.ok().eTag(etag(monitor)).body(monitor);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MonitorResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MonitorRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        MonitorResponse updated = service.update(id, request, parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(etag(updated)).body(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String etag(MonitorResponse monitor) {
        return "\"" + monitor.version() + "\"";
    }

    /** Parses {@code "3"} or {@code W/"3"}; {@code *} or absent means "no precondition". */
    static Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
            return null;
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new PreconditionFailedException("If-Match does not match the current version");
        }
    }

    private static Sort toSort(String sort) {
        String[] parts = sort.split(",");
        Sort.Direction direction = parts.length > 1 && parts[1].equals("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Secondary sort on id keeps page boundaries stable when primary values are equal.
        return Sort.by(direction, SORT_FIELDS.get(parts[0])).and(Sort.by("id"));
    }
}
