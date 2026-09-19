package com.example.eventplatform.domain.port.in;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Porta de entrada: recebe eventos de qualquer consumidor (REST, gRPC, CLI) e os registra
 * de forma durável no log de eventos. O processamento pesado acontece de forma assíncrona,
 * o que mantém a latência de resposta baixa e desacopla picos de carga do banco de dados.
 */
public interface IngestEventUseCase {

    /**
     * @throws com.example.eventplatform.application.EventLogUnavailableException se o log
     *         de eventos não confirmar a escrita dentro do prazo (o cliente deve tentar novamente).
     */
    IngestResult ingest(IngestCommand command);

    /** Processa cada item de forma independente; falhas não invalidam o lote inteiro. */
    List<IngestResult> ingestBatch(List<IngestCommand> commands);

    record IngestCommand(
            String tenantId,
            String idempotencyKey,
            String type,
            String source,
            Instant occurredAt,
            String payload) {
    }

    record IngestResult(UUID eventId, Outcome outcome, String error) {

        public static IngestResult accepted(UUID id) {
            return new IngestResult(id, Outcome.ACCEPTED, null);
        }

        public static IngestResult duplicate(UUID id) {
            return new IngestResult(id, Outcome.DUPLICATE, null);
        }

        public static IngestResult rejected(UUID id, String error) {
            return new IngestResult(id, Outcome.REJECTED, error);
        }

        public static IngestResult retry(UUID id, String error) {
            return new IngestResult(id, Outcome.RETRY, error);
        }
    }

    enum Outcome {
        ACCEPTED,
        /** Já recebido anteriormente com a mesma chave de idempotência. */
        DUPLICATE,
        /** Inválido: não adianta reenviar sem corrigir. */
        REJECTED,
        /** Falha transitória: reenviar com a mesma chave de idempotência é seguro. */
        RETRY
    }
}
