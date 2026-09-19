package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventPage;
import com.example.eventplatform.domain.model.EventQuery;
import com.example.eventplatform.domain.port.in.QueryEventsUseCase;
import com.example.eventplatform.domain.port.out.EventRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class QueryEventsService implements QueryEventsUseCase {

    private final EventRepository repository;

    public QueryEventsService(EventRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Optional<Event> findById(String tenantId, UUID id) {
        // Um evento de outro tenant é tratado como inexistente: não revelamos sua existência.
        return repository.findById(id).filter(e -> e.belongsTo(tenantId));
    }

    @Override
    public EventPage search(EventQuery query) {
        return repository.search(query);
    }
}
