package com.example.eventplatform.support;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventPage;
import com.example.eventplatform.domain.model.EventQuery;
import com.example.eventplatform.domain.port.out.EventLogPublisher;
import com.example.eventplatform.domain.port.out.EventRepository;
import com.example.eventplatform.domain.port.out.IdempotencyStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Dublês em memória das portas de saída: testes do núcleo rodam sem Spring nem Docker. */
public final class Fakes {

    private Fakes() {
    }

    public static final class InMemoryPublisher implements EventLogPublisher {
        public final List<Event> accepted = new ArrayList<>();
        public final List<Event> processed = new ArrayList<>();
        public Function<Event, CompletableFuture<Void>> behavior = e -> CompletableFuture.completedFuture(null);

        @Override
        public synchronized CompletableFuture<Void> publishAccepted(Event event) {
            var f = behavior.apply(event);
            accepted.add(event);
            return f;
        }

        @Override
        public synchronized CompletableFuture<Void> publishProcessed(Event event) {
            processed.add(event);
            return behavior.apply(event);
        }
    }

    public static final class InMemoryIdempotency implements IdempotencyStore {
        public final Set<UUID> keys = ConcurrentHashMap.newKeySet();

        @Override
        public boolean registerIfAbsent(UUID eventId) {
            return keys.add(eventId);
        }

        @Override
        public void release(UUID eventId) {
            keys.remove(eventId);
        }
    }

    /** Simula a chave (id, occurredAt) com ON CONFLICT DO NOTHING. */
    public static final class InMemoryRepository implements EventRepository {
        public final Map<String, Event> rows = new ConcurrentHashMap<>();

        @Override
        public int saveAll(List<Event> events) {
            int inserted = 0;
            for (Event e : events) {
                if (rows.putIfAbsent(e.id() + "@" + e.occurredAt(), e) == null) {
                    inserted++;
                }
            }
            return inserted;
        }

        @Override
        public Optional<Event> findById(UUID id) {
            return rows.values().stream().filter(e -> e.id().equals(id)).findFirst();
        }

        @Override
        public EventPage search(EventQuery q) {
            var sorted = rows.values().stream()
                    .filter(e -> e.tenantId().equals(q.tenantId()))
                    .filter(e -> q.type() == null || e.type().equals(q.type()))
                    .sorted(Comparator.comparing(Event::occurredAt).thenComparing(Event::id).reversed())
                    .limit(q.limit())
                    .toList();
            return new EventPage(sorted, null);
        }
    }
}
