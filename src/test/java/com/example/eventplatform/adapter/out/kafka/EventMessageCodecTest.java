package com.example.eventplatform.adapter.out.kafka;

import com.example.eventplatform.domain.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventMessageCodecTest {

    private final EventMessageCodec codec = new EventMessageCodec(new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    @Test
    void roundTrip() {
        var now = Instant.parse("2026-11-03T12:00:00.123Z");
        var event = Event.accepted(UUID.randomUUID(), "t1", "order.created", "checkout", now, now, "{\"a\":1}");

        String json = codec.encode(event);

        assertThat(json).contains("\"schemaVersion\":1");
        assertThat(codec.decode(json)).isEqualTo(event);
    }

    @Test
    void malformedMessageIsIllegalArgument() {
        assertThatThrownBy(() -> codec.decode("{not json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("{\"schemaVersion\":99}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partitionKeyCombinesTenantAndSource() {
        var now = Instant.now();
        var event = Event.accepted(UUID.randomUUID(), "t1", "order.created", "checkout", now, now, "{}");
        assertThat(KafkaEventLogPublisher.partitionKey(event)).isEqualTo("t1|checkout");
    }
}
