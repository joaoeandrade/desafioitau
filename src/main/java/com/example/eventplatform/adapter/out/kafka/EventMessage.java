package com.example.eventplatform.adapter.out.kafka;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato público da mensagem no Kafka. Separado do modelo de domínio para que o domínio
 * possa evoluir sem quebrar consumidores. Mudanças incompatíveis exigem nova {@code schemaVersion}
 * (ver docs/adr/0004-contratos-e-versionamento.md).
 */
public record EventMessage(
        int schemaVersion,
        UUID id,
        String tenantId,
        String type,
        String source,
        Instant occurredAt,
        Instant receivedAt,
        String payload,
        EventStatus status) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static EventMessage from(Event e) {
        return new EventMessage(CURRENT_SCHEMA_VERSION, e.id(), e.tenantId(), e.type(), e.source(),
                e.occurredAt(), e.receivedAt(), e.payload(), e.status());
    }

    public Event toDomain() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion não suportada: " + schemaVersion);
        }
        return new Event(id, tenantId, type, source, occurredAt, receivedAt, payload, status);
    }
}
