package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.support.Fakes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueryEventsServiceTest {

    @Test
    void doesNotExposeEventsFromOtherTenants() {
        var repo = new Fakes.InMemoryRepository();
        var now = Instant.now();
        var event = Event.accepted(UUID.randomUUID(), "tenant-a", "order.created", "s", now, now, "{}");
        repo.saveAll(List.of(event));
        var service = new QueryEventsService(repo);

        assertThat(service.findById("tenant-a", event.id())).isPresent();
        assertThat(service.findById("tenant-b", event.id())).isEmpty();
    }
}
