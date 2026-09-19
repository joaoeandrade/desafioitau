package com.example.eventplatform.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void createsAcceptedEvent() {
        var e = Event.accepted(UUID.randomUUID(), "t1", "order.created", "checkout", NOW, NOW, "{}");
        assertThat(e.status()).isEqualTo(EventStatus.ACCEPTED);
        assertThat(e.belongsTo("t1")).isTrue();
        assertThat(e.belongsTo("t2")).isFalse();
    }

    @Test
    void markProcessedIsIdempotentAndImmutable() {
        var accepted = Event.accepted(UUID.randomUUID(), "t1", "order.created", "checkout", NOW, NOW, "{}");
        var processed = accepted.markProcessed();
        assertThat(accepted.status()).isEqualTo(EventStatus.ACCEPTED);
        assertThat(processed.status()).isEqualTo(EventStatus.PROCESSED);
        assertThat(processed.markProcessed()).isSameAs(processed);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Order.Created", "order", "order.", ".created", "order created", "1order.created"})
    void rejectsInvalidTypes(String type) {
        assertThatThrownBy(() -> Event.accepted(UUID.randomUUID(), "t1", type, "s", NOW, NOW, "{}"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsOversizedPayload() {
        String big = "x".repeat(Event.MAX_PAYLOAD_BYTES + 1);
        assertThatThrownBy(() -> Event.accepted(UUID.randomUUID(), "t1", "order.created", "s", NOW, NOW, big))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void rejectsBlankTenant() {
        assertThatThrownBy(() -> Event.accepted(UUID.randomUUID(), " ", "order.created", "s", NOW, NOW, "{}"))
                .isInstanceOf(DomainValidationException.class);
    }
}
