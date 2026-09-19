package com.example.eventplatform.domain.port.in;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventPage;
import com.example.eventplatform.domain.model.EventQuery;

import java.util.Optional;
import java.util.UUID;

public interface QueryEventsUseCase {

    /** Retorna o evento somente se pertencer ao tenant solicitante (isolamento multi-tenant). */
    Optional<Event> findById(String tenantId, UUID id);

    EventPage search(EventQuery query);
}
