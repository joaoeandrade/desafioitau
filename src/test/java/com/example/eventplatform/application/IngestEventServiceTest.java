package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.DomainValidationException;
import com.example.eventplatform.domain.model.EventStatus;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.IngestCommand;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.Outcome;
import com.example.eventplatform.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    private Fakes.InMemoryPublisher publisher;
    private Fakes.InMemoryIdempotency idempotency;
    private IngestEventService service;

    @BeforeEach
    void setUp() {
        publisher = new Fakes.InMemoryPublisher();
        idempotency = new Fakes.InMemoryIdempotency();
        service = new IngestEventService(publisher, idempotency, Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMillis(100), Duration.ofMinutes(5), 500);
    }

    private static IngestCommand cmd(String key) {
        return new IngestCommand("tenant-a", key, "order.created", "checkout", null, "{\"total\":10}");
    }

    @Test
    void acceptsAndPublishesEvent() {
        var result = service.ingest(cmd(null));

        assertThat(result.outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(publisher.accepted).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(result.eventId());
            assertThat(e.status()).isEqualTo(EventStatus.ACCEPTED);
            assertThat(e.occurredAt()).isEqualTo(NOW); // padrão = horário de recebimento
            assertThat(e.tenantId()).isEqualTo("tenant-a");
        });
    }

    @Test
    void sameIdempotencyKeyIsReportedAsDuplicate() {
        var first = service.ingest(cmd("abc"));
        var second = service.ingest(cmd("abc"));

        assertThat(first.outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(second.outcome()).isEqualTo(Outcome.DUPLICATE);
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(publisher.accepted).hasSize(1);
    }

    @Test
    void eventsWithoutIdempotencyKeyDoNotTouchTheStore() {
        service.ingest(cmd(null));
        assertThat(idempotency.keys).isEmpty();
    }

    @Test
    void brokerTimeoutReleasesKeyAndSignalsUnavailable() {
        publisher.behavior = e -> new CompletableFuture<>(); // nunca completa

        assertThatThrownBy(() -> service.ingest(cmd("k1"))).isInstanceOf(EventLogUnavailableException.class);
        assertThat(idempotency.keys).isEmpty(); // cliente pode reenviar com a mesma chave

        publisher.behavior = e -> CompletableFuture.completedFuture(null);
        assertThat(service.ingest(cmd("k1")).outcome()).isEqualTo(Outcome.ACCEPTED);
    }

    @Test
    void brokerFailureMarksBatchItemAsRetry() {
        publisher.behavior = e -> CompletableFuture.failedFuture(new RuntimeException("broker down"));

        var results = service.ingestBatch(List.of(cmd("a"), cmd("b")));

        assertThat(results).extracting(r -> r.outcome()).containsOnly(Outcome.RETRY);
    }

    @Test
    void invalidItemsAreRejectedWithoutAffectingOthers() {
        var invalid = new IngestCommand("tenant-a", null, "INVALID", "checkout", null, "{}");

        var results = service.ingestBatch(List.of(cmd(null), invalid, cmd(null)));

        assertThat(results).extracting(r -> r.outcome())
                .containsExactly(Outcome.ACCEPTED, Outcome.REJECTED, Outcome.ACCEPTED);
        assertThat(publisher.accepted).hasSize(2);
    }

    @Test
    void rejectsEventsTooFarInTheFuture() {
        var future = new IngestCommand("tenant-a", null, "order.created", "checkout",
                NOW.plus(Duration.ofHours(1)), "{}");

        assertThat(service.ingestBatch(List.of(future)).getFirst().outcome()).isEqualTo(Outcome.REJECTED);
    }

    @Test
    void rejectsOversizedBatch() {
        var tooMany = Collections.nCopies(501, cmd(null));
        assertThatThrownBy(() -> service.ingestBatch(tooMany)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void missingTenantIsRejected() {
        var noTenant = new IngestCommand(null, null, "order.created", "checkout", null, "{}");
        assertThat(service.ingest(noTenant).outcome()).isEqualTo(Outcome.REJECTED);
    }
}
