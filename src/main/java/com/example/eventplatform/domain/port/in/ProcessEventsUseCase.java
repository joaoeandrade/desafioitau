package com.example.eventplatform.domain.port.in;

import com.example.eventplatform.domain.model.Event;

import java.util.List;

/**
 * Porta de entrada acionada pelo consumidor do log de eventos: persiste em lote e
 * propaga os eventos processados para integrações downstream.
 */
public interface ProcessEventsUseCase {

    ProcessingReport process(List<Event> events);

    record ProcessingReport(int received, int uniqueInBatch, int persisted, int published) {
    }
}
