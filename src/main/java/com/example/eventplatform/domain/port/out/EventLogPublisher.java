package com.example.eventplatform.domain.port.out;

import com.example.eventplatform.domain.model.Event;

import java.util.concurrent.CompletableFuture;

/**
 * Log de eventos durável e particionado (implementação padrão: Kafka).
 * O futuro só completa após confirmação do broker (acks=all).
 */
public interface EventLogPublisher {

    CompletableFuture<Void> publishAccepted(Event event);

    CompletableFuture<Void> publishProcessed(Event event);
}
