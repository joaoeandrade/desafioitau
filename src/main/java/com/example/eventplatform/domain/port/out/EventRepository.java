package com.example.eventplatform.domain.port.out;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventPage;
import com.example.eventplatform.domain.model.EventQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository {

    /**
     * Persiste em lote de forma idempotente: eventos já existentes (mesmo id + occurredAt)
     * são ignorados silenciosamente.
     *
     * @return quantidade de linhas efetivamente inseridas quando o driver informa; caso
     *         contrário, o tamanho do lote enviado.
     */
    int saveAll(List<Event> events);

    Optional<Event> findById(UUID id);

    EventPage search(EventQuery query);
}
