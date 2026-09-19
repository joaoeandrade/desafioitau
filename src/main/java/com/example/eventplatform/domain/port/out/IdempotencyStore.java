package com.example.eventplatform.domain.port.out;

import java.util.UUID;

/**
 * Registro de curta duração de ids já aceitos, usado para responder rapidamente a
 * reenvios de clientes. É uma otimização: a garantia final de unicidade é do banco.
 */
public interface IdempotencyStore {

    /**
     * @return {@code true} se o id foi registrado agora (primeira vez); {@code false} se já existia.
     *         Implementações devem falhar "aberto" (retornar true) quando indisponíveis.
     */
    boolean registerIfAbsent(UUID eventId);

    /** Desfaz o registro quando a publicação falha, permitindo que o cliente tente novamente. */
    void release(UUID eventId);
}
