package com.example.eventplatform.adapter.out.cache;

import com.example.eventplatform.domain.port.out.IdempotencyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Usado quando a checagem rápida está desligada; o banco continua garantindo unicidade. */
@Component
@ConditionalOnProperty(name = "platform.idempotency.enabled", havingValue = "false")
public class NoOpIdempotencyStore implements IdempotencyStore {

    @Override
    public boolean registerIfAbsent(UUID eventId) {
        return true;
    }

    @Override
    public void release(UUID eventId) {
        // nada a liberar
    }
}
