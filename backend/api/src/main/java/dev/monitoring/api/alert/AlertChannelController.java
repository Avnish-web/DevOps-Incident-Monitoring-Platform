package dev.monitoring.api.alert;

import dev.monitoring.api.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Alert channels: where incident notifications are sent. */
@RestController
@RequestMapping(path = "/api/v1/alert-channels", produces = MediaType.APPLICATION_JSON_VALUE)
public class AlertChannelController {

    private final AlertChannelService service;

    public AlertChannelController(AlertChannelService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AlertChannelResponse> create(@Valid @RequestBody AlertChannelRequest request) {
        AlertChannelResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<AlertChannelResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public AlertChannelResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AlertChannelResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody AlertChannelRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Queues a test notification (202: delivery happens asynchronously in the worker). */
    @PostMapping("/{id}/test")
    public ResponseEntity<AlertDeliveryResponse> test(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.sendTest(id));
    }

    @GetMapping("/{id}/deliveries")
    public PageResponse<AlertDeliveryResponse> deliveries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.deliveries(id, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
    }
}
