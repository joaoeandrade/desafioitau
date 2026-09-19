package com.example.eventplatform.domain.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventIdsTest {

    @Test
    void generatesVersion7Uuids() {
        UUID id = EventIds.newId();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void uuidV7IsTimeOrdered() throws InterruptedException {
        UUID first = EventIds.newId();
        Thread.sleep(2);
        UUID second = EventIds.newId();
        // Os 48 bits mais significativos são o timestamp em ms.
        assertThat(second.getMostSignificantBits() >>> 16).isGreaterThan(first.getMostSignificantBits() >>> 16);
    }

    @Test
    void generatesUniqueIds() {
        var ids = new HashSet<UUID>();
        for (int i = 0; i < 100_000; i++) {
            ids.add(EventIds.newId());
        }
        assertThat(ids).hasSize(100_000);
    }

    @Test
    void idempotencyKeyIsDeterministicAndScopedByTenant() {
        assertThat(EventIds.fromIdempotencyKey("t1", "k")).isEqualTo(EventIds.fromIdempotencyKey("t1", "k"));
        assertThat(EventIds.fromIdempotencyKey("t1", "k")).isNotEqualTo(EventIds.fromIdempotencyKey("t2", "k"));
    }
}
