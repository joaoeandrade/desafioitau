package com.example.eventplatform.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Critérios de busca com paginação por cursor (keyset), que mantém custo constante
 * independentemente da profundidade da página — ao contrário de OFFSET.
 */
public record EventQuery(
        String tenantId,
        String type,
        Instant from,
        Instant to,
        int limit,
        Cursor after) {

    public static final int MAX_LIMIT = 500;

    public EventQuery {
        Objects.requireNonNull(tenantId, "tenantId é obrigatório");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new DomainValidationException("limit deve estar entre 1 e " + MAX_LIMIT);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new DomainValidationException("from deve ser anterior a to");
        }
    }

    /** Posição da última linha retornada (ordenação: occurredAt DESC, id DESC). */
    public record Cursor(Instant occurredAt, UUID id) {
    }
}
