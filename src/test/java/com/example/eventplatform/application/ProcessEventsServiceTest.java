package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventStatus;
import com.example.eventplatform.support.Fakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessEventsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    private Fakes.InMemoryRepository repository;
    private Fakes.InMemoryPublisher publisher;
    private ProcessEventsService service;

    @BeforeEach
    void setUp() {
        repository = new Fakes.InMemoryRepository();
        publisher = new Fakes.InMemoryPublisher();
        service = new ProcessEventsService(repository, publisher);
    }

    private static Event event() {
        return Event.accepted(UUID.randomUUID(), "t1", "order.created", "checkout", NOW, NOW, "{}");
    }

    @Test
    void persistsAndPublishesProcessedEvents() {
        var report = service.process(List.of(event(), event()));

        assertThat(report.persisted()).isEqualTo(2);
        assertThat(repository.rows.values()).allMatch(e -> e.status() == EventStatus.PROCESSED);
        assertThat(publisher.processed).hasSize(2);
    }

    @Test
    void deduplicatesWithinBatch() {
        var e = event();
        var report = service.process(List.of(e, e, e));

        assertThat(report.received()).isEqualTo(3);
        assertThat(report.uniqueInBatch()).isEqualTo(1);
        assertThat(publisher.processed).hasSize(1);
    }

    @Test
    void redeliveryIsIdempotent() {
        var e = event();
        service.process(List.of(e));
        var second = service.process(List.of(e));

        assertThat(second.persisted()).isZero();
        assertThat(repository.rows).hasSize(1);
    }

    @Test
    void publishFailurePropagatesSoBatchIsRetried() {
        publisher.behavior = ev -> CompletableFuture.failedFuture(new RuntimeException("down"));

        assertThatThrownBy(() -> service.process(List.of(event()))).isInstanceOf(CompletionException.class);
    }

    @Test
    void emptyBatchIsNoOp() {
        assertThat(service.process(List.of()).received()).isZero();
    }
}
