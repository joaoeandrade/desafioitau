package com.example.eventplatform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/** Configuração tipada e validada na inicialização (falha rápido se algo estiver errado). */
@Validated
@ConfigurationProperties(prefix = "platform")
public record PlatformProperties(
        @Valid @NotNull Kafka kafka,
        @Valid @NotNull Ingestion ingestion,
        @Valid @NotNull Idempotency idempotency,
        @Valid @NotNull Integration integration,
        @Valid @NotNull Security security) {

    public record Kafka(
            @NotBlank String ingestedTopic,
            @NotBlank String processedTopic,
            @Min(1) int partitions,
            @Min(1) int replicas,
            @Min(1) int persistenceConcurrency,
            @Min(1) int integrationConcurrency) {
    }

    public record Ingestion(
            @NotNull Duration publishTimeout,
            @NotNull Duration maxClockSkew,
            @Min(1) @Max(1000) int maxBatchSize) {
    }

    public record Idempotency(
            @DefaultValue("true") boolean enabled,
            @NotNull Duration ttl,
            @NotBlank String keyPrefix) {
    }

    public record Integration(
            boolean enabled,
            URI webhookUrl,
            /** Segredo para assinar o corpo (HMAC-SHA256) no header X-Signature. */
            String webhookSecret,
            @DefaultValue Set<String> subscribedTypes,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout) {
    }

    public record Security(
            /** Somente para desenvolvimento local: segredo HMAC para validar JWTs HS256. */
            String devHmacSecret) {
    }
}
