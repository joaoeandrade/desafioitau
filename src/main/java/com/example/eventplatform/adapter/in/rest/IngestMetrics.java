package com.example.eventplatform.adapter.in.rest;

import com.example.eventplatform.domain.port.in.IngestEventUseCase.IngestResult;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.Outcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** Métricas de negócio da ingestão: {@code events_ingested_total{outcome=...}}. */
@Component
public class IngestMetrics {

    private final Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);

    public IngestMetrics(MeterRegistry registry) {
        for (Outcome o : Outcome.values()) {
            counters.put(o, Counter.builder("events.ingested")
                    .description("Eventos recebidos pela API por resultado")
                    .tag("outcome", o.name().toLowerCase())
                    .register(registry));
        }
    }

    public void record(IngestResult result) {
        counters.get(result.outcome()).increment();
    }
}
