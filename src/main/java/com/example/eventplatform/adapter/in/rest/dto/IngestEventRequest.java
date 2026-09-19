package com.example.eventplatform.adapter.in.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IngestEventRequest(
        @Schema(example = "order.created", description = "Tipo no formato dominio.acao")
        @NotBlank @Size(max = 128) String type,

        @Schema(example = "checkout-service")
        @NotBlank @Size(max = 128) String source,

        @Schema(description = "Momento em que o fato ocorreu na origem; padrão: horário de recebimento")
        Instant occurredAt,

        @Schema(description = "Chave de idempotência por item (usada em lotes). Em envios unitários, prefira o header Idempotency-Key.")
        @Size(max = 128) String idempotencyKey,

        @Schema(description = "Conteúdo JSON livre do evento (até 64 KiB)")
        @NotNull JsonNode payload) {
}
