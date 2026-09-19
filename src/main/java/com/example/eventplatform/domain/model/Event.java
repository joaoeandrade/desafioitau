package com.example.eventplatform.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Agregado central do domínio. Imutável: cada transição de estado gera uma nova instância.
 *
 * <p>O par (id, occurredAt) é a chave natural usada para deduplicação na persistência,
 * garantindo semântica efetivamente-uma-vez sobre uma entrega pelo-menos-uma-vez do Kafka.
 */
public record Event(
        UUID id,
        String tenantId,
        String type,
        String source,
        Instant occurredAt,
        Instant receivedAt,
        String payload,
        EventStatus status) {

    private static final Pattern TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+$");
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;

    public Event {
        Objects.requireNonNull(id, "id é obrigatório");
        requireText(tenantId, "tenantId");
        requireText(type, "type");
        requireText(source, "source");
        Objects.requireNonNull(occurredAt, "occurredAt é obrigatório");
        Objects.requireNonNull(receivedAt, "receivedAt é obrigatório");
        Objects.requireNonNull(payload, "payload é obrigatório");
        Objects.requireNonNull(status, "status é obrigatório");

        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new DomainValidationException(
                    "type deve seguir o formato 'dominio.acao' em minúsculas (ex.: order.created)");
        }
        if (payload.length() > MAX_PAYLOAD_BYTES) {
            throw new DomainValidationException("payload excede o limite de " + MAX_PAYLOAD_BYTES + " bytes");
        }
    }

    public static Event accepted(UUID id, String tenantId, String type, String source,
                                 Instant occurredAt, Instant receivedAt, String payload) {
        return new Event(id, tenantId, type, source, occurredAt, receivedAt, payload, EventStatus.ACCEPTED);
    }

    public Event markProcessed() {
        if (status == EventStatus.PROCESSED) {
            return this;
        }
        return new Event(id, tenantId, type, source, occurredAt, receivedAt, payload, EventStatus.PROCESSED);
    }

    public boolean belongsTo(String tenant) {
        return tenantId.equals(tenant);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " é obrigatório");
        }
    }
}
