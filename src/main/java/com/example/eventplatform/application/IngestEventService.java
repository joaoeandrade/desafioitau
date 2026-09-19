package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.DomainValidationException;
import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventIds;
import com.example.eventplatform.domain.port.in.IngestEventUseCase;
import com.example.eventplatform.domain.port.out.EventLogPublisher;
import com.example.eventplatform.domain.port.out.IdempotencyStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Caminho crítico de latência. Faz apenas validação, deduplicação rápida e escrita no log
 * (Kafka). Não toca o banco de dados — por isso suporta picos muito acima da capacidade
 * de escrita do PostgreSQL: o Kafka absorve o pico e os consumidores drenam no seu ritmo.
 *
 * <p>Classe sem dependência de framework (arquitetura hexagonal): é instanciada na
 * configuração Spring e testável com dublês simples.
 */
public final class IngestEventService implements IngestEventUseCase {

    private final EventLogPublisher publisher;
    private final IdempotencyStore idempotencyStore;
    private final Clock clock;
    private final Duration publishTimeout;
    private final Duration maxClockSkew;
    private final int maxBatchSize;

    public IngestEventService(EventLogPublisher publisher,
                              IdempotencyStore idempotencyStore,
                              Clock clock,
                              Duration publishTimeout,
                              Duration maxClockSkew,
                              int maxBatchSize) {
        this.publisher = Objects.requireNonNull(publisher);
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore);
        this.clock = Objects.requireNonNull(clock);
        this.publishTimeout = Objects.requireNonNull(publishTimeout);
        this.maxClockSkew = Objects.requireNonNull(maxClockSkew);
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public IngestResult ingest(IngestCommand command) {
        IngestResult result = ingestBatch(List.of(command)).getFirst();
        if (result.outcome() == Outcome.RETRY) {
            throw new EventLogUnavailableException(result.error(), null);
        }
        return result;
    }

    @Override
    public List<IngestResult> ingestBatch(List<IngestCommand> commands) {
        if (commands.isEmpty()) {
            return List.of();
        }
        if (commands.size() > maxBatchSize) {
            throw new DomainValidationException("lote excede o máximo de " + maxBatchSize + " eventos");
        }

        Instant now = clock.instant();
        List<Pending> pending = new ArrayList<>(commands.size());

        // Fase 1: validar, deduplicar e disparar todas as publicações em paralelo.
        for (IngestCommand command : commands) {
            pending.add(prepareAndPublish(command, now));
        }

        // Fase 2: aguardar as confirmações do broker com um prazo único para o lote inteiro.
        long deadline = System.nanoTime() + publishTimeout.toNanos();
        List<IngestResult> results = new ArrayList<>(pending.size());
        for (Pending p : pending) {
            results.add(p.future == null ? p.immediateResult : await(p, deadline));
        }
        return results;
    }

    private Pending prepareAndPublish(IngestCommand command, Instant now) {
        Event event;
        try {
            event = toEvent(command, now);
        } catch (DomainValidationException | NullPointerException e) {
            return Pending.done(IngestResult.rejected(null, e.getMessage()));
        }

        // Sem chave do cliente o id é aleatório: não há reenvio a detectar, então não gastamos o Redis.
        boolean tracked = hasIdempotencyKey(command);
        if (tracked && !idempotencyStore.registerIfAbsent(event.id())) {
            return Pending.done(IngestResult.duplicate(event.id()));
        }

        try {
            return new Pending(event, publisher.publishAccepted(event), null, tracked);
        } catch (RuntimeException e) {
            releaseIfTracked(event, tracked);
            return Pending.done(IngestResult.retry(event.id(), "log de eventos indisponível"));
        }
    }

    private void releaseIfTracked(Event event, boolean tracked) {
        if (tracked) {
            idempotencyStore.release(event.id());
        }
    }

    private static boolean hasIdempotencyKey(IngestCommand c) {
        return c.idempotencyKey() != null && !c.idempotencyKey().isBlank();
    }

    private IngestResult await(Pending p, long deadlineNanos) {
        try {
            long remaining = Math.max(0, deadlineNanos - System.nanoTime());
            p.future.get(remaining, TimeUnit.NANOSECONDS);
            return IngestResult.accepted(p.event.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseIfTracked(p.event, p.tracked);
            return IngestResult.retry(p.event.id(), "requisição interrompida");
        } catch (Exception e) {
            // Timeout ou falha do broker: liberamos a chave para que o reenvio do cliente seja aceito.
            // Se a mensagem tiver sido gravada mesmo assim, a persistência deduplica por (id, occurredAt).
            releaseIfTracked(p.event, p.tracked);
            return IngestResult.retry(p.event.id(), "log de eventos não confirmou a escrita a tempo");
        }
    }

    private Event toEvent(IngestCommand c, Instant now) {
        if (c.tenantId() == null || c.tenantId().isBlank()) {
            throw new DomainValidationException("tenant não identificado");
        }
        Instant occurredAt = c.occurredAt() != null ? c.occurredAt() : now;
        if (occurredAt.isAfter(now.plus(maxClockSkew))) {
            throw new DomainValidationException("occurredAt está no futuro além da tolerância de " + maxClockSkew);
        }
        var id = hasIdempotencyKey(c)
                ? EventIds.fromIdempotencyKey(c.tenantId(), c.idempotencyKey())
                : EventIds.newId();
        return Event.accepted(id, c.tenantId(), c.type(), c.source(), occurredAt, now, c.payload());
    }

    private record Pending(Event event, CompletableFuture<Void> future, IngestResult immediateResult, boolean tracked) {
        static Pending done(IngestResult result) {
            return new Pending(null, null, result, false);
        }
    }
}
