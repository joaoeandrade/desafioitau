package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.port.in.ProcessEventsUseCase;
import com.example.eventplatform.domain.port.out.EventLogPublisher;
import com.example.eventplatform.domain.port.out.EventRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Drena o log de eventos em lotes. Lotes grandes (centenas a milhares de registros) amortizam
 * o custo de round-trip ao banco: 20k eventos/s ≈ 20–40 INSERTs em lote por segundo por réplica.
 *
 * <p>Semântica: entrega pelo-menos-uma-vez do Kafka + escrita idempotente = efeito exatamente-uma-vez
 * no estado persistido. Se qualquer etapa falhar, a exceção sobe e o lote inteiro é reprocessado.
 */
public final class ProcessEventsService implements ProcessEventsUseCase {

    private final EventRepository repository;
    private final EventLogPublisher publisher;

    public ProcessEventsService(EventRepository repository, EventLogPublisher publisher) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
    }

    @Override
    public ProcessingReport process(List<Event> events) {
        if (events.isEmpty()) {
            return new ProcessingReport(0, 0, 0, 0);
        }

        // Deduplica dentro do lote (reentregas do Kafka podem trazer o mesmo evento duas vezes).
        Map<UUID, Event> unique = new LinkedHashMap<>(events.size() * 2);
        for (Event e : events) {
            unique.putIfAbsent(e.id(), e.markProcessed());
        }
        List<Event> processed = new ArrayList<>(unique.values());

        int persisted = repository.saveAll(processed);

        // Fan-out para integrações: publica no tópico de processados e aguarda todas as confirmações.
        CompletableFuture<?>[] futures = processed.stream()
                .map(publisher::publishProcessed)
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join();

        return new ProcessingReport(events.size(), processed.size(), persisted, processed.size());
    }
}
