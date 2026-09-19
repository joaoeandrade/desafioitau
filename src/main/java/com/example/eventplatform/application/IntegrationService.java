package com.example.eventplatform.application;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.port.out.DownstreamNotifier;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Roteia eventos processados para sistemas externos conforme os tipos assinados.
 * Uma lista vazia de tipos significa "todos".
 */
public final class IntegrationService {

    private final DownstreamNotifier notifier;
    private final Set<String> subscribedTypes;

    public IntegrationService(DownstreamNotifier notifier, Set<String> subscribedTypes) {
        this.notifier = Objects.requireNonNull(notifier);
        this.subscribedTypes = Set.copyOf(subscribedTypes);
    }

    public int dispatch(List<Event> events) {
        int sent = 0;
        for (Event event : events) {
            if (subscribedTypes.isEmpty() || subscribedTypes.contains(event.type())) {
                notifier.notify(event);
                sent++;
            }
        }
        return sent;
    }
}
