package com.example.eventplatform.adapter.in.rest;

import com.example.eventplatform.adapter.in.rest.dto.BatchIngestRequest;
import com.example.eventplatform.adapter.in.rest.dto.EventResponse;
import com.example.eventplatform.adapter.in.rest.dto.IngestEventRequest;
import com.example.eventplatform.adapter.in.rest.dto.IngestResponses;
import com.example.eventplatform.domain.model.EventQuery;
import com.example.eventplatform.domain.port.in.IngestEventUseCase;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.IngestCommand;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.IngestResult;
import com.example.eventplatform.domain.port.in.QueryEventsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/events")
@Tag(name = "Events")
@SecurityRequirement(name = "bearer-jwt")
public class EventController {

    private final IngestEventUseCase ingest;
    private final QueryEventsUseCase query;
    private final IngestMetrics metrics;

    public EventController(IngestEventUseCase ingest, QueryEventsUseCase query, IngestMetrics metrics) {
        this.ingest = ingest;
        this.query = query;
        this.metrics = metrics;
    }

    @Operation(summary = "Ingere um evento",
            description = "Retorna 202 quando o evento foi gravado de forma durável no log; "
                    + "200 quando a mesma Idempotency-Key já havia sido recebida.")
    @PostMapping
    public ResponseEntity<IngestResponses.Single> ingest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey,
            @Valid @RequestBody IngestEventRequest request) {

        String tenant = TenantResolver.tenantOf(jwt);
        IngestResult result = ingest.ingest(toCommand(tenant, request, idempotencyKey));
        count(result);

        return switch (result.outcome()) {
            case ACCEPTED -> ResponseEntity.accepted().body(IngestResponses.Single.of(result));
            case DUPLICATE -> ResponseEntity.ok(IngestResponses.Single.of(result));
            case REJECTED -> throw new IllegalArgumentException(result.error());
            case RETRY -> throw new IllegalStateException("estado inesperado: RETRY deve ser lançado pelo caso de uso");
        };
    }

    @Operation(summary = "Ingere até 500 eventos em uma única chamada",
            description = "Cada item é tratado de forma independente. 202 se todos foram aceitos; 207 caso contrário.")
    @PostMapping("/batch")
    public ResponseEntity<IngestResponses.Batch> ingestBatch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BatchIngestRequest request) {

        String tenant = TenantResolver.tenantOf(jwt);
        List<IngestCommand> commands = request.events().stream()
                .map(e -> toCommand(tenant, e, e.idempotencyKey()))
                .toList();

        List<IngestResult> results = ingest.ingestBatch(commands);
        results.forEach(this::count);

        var body = IngestResponses.Batch.of(results);
        return ResponseEntity.status(body.allSucceeded() ? HttpStatus.ACCEPTED : HttpStatus.MULTI_STATUS).body(body);
    }

    @Operation(summary = "Consulta um evento processado")
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> findById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String tenant = TenantResolver.tenantOf(jwt);
        return query.findById(tenant, id)
                .map(EventResponse::of)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Lista eventos do tenant (paginação por cursor, mais recentes primeiro)")
    @GetMapping
    public EventResponse.Page search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 128) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "50") @Min(1) @Max(EventQuery.MAX_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {

        String tenant = TenantResolver.tenantOf(jwt);
        var page = query.search(new EventQuery(tenant, type, from, to, limit, CursorCodec.decode(cursor)));
        return new EventResponse.Page(
                page.items().stream().map(EventResponse::of).toList(),
                CursorCodec.encode(page.nextCursor()));
    }

    private static IngestCommand toCommand(String tenant, IngestEventRequest r, String idempotencyKey) {
        return new IngestCommand(tenant, idempotencyKey, r.type(), r.source(), r.occurredAt(), r.payload().toString());
    }

    private void count(IngestResult result) {
        metrics.record(result);
    }
}
