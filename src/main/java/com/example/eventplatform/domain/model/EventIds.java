package com.example.eventplatform.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Geração de identificadores de eventos.
 *
 * <ul>
 *   <li>{@link #newId()} — UUIDv7 (RFC 9562): ordenado no tempo, reduz fragmentação de índices B-tree
 *       e mantém inserções "append-only" no PostgreSQL.</li>
 *   <li>{@link #fromIdempotencyKey(String, String)} — UUID determinístico derivado da chave de
 *       idempotência do cliente, escopado por tenant: o mesmo reenvio gera o mesmo id.</li>
 * </ul>
 */
public final class EventIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private EventIds() {
    }

    public static UUID newId() {
        long millis = System.currentTimeMillis();
        long randA = RANDOM.nextInt(1 << 12);
        long randB = RANDOM.nextLong();

        long msb = (millis << 16) | (0x7L << 12) | randA;          // 48 bits tempo | versão 7 | 12 bits aleatórios
        long lsb = (randB & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // variante IETF
        return new UUID(msb, lsb);
    }

    public static UUID fromIdempotencyKey(String tenantId, String idempotencyKey) {
        byte[] seed = (tenantId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(seed);
    }
}
