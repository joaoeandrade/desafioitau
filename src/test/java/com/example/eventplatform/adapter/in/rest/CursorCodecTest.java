package com.example.eventplatform.adapter.in.rest;

import com.example.eventplatform.domain.model.EventQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    @Test
    void roundTripPreservesNanosecondPrecision() {
        var cursor = new EventQuery.Cursor(Instant.parse("2026-11-03T12:00:00.123456789Z"), UUID.randomUUID());
        assertThat(CursorCodec.decode(CursorCodec.encode(cursor))).isEqualTo(cursor);
    }

    @Test
    void nullAndBlankAreAbsent() {
        assertThat(CursorCodec.encode(null)).isNull();
        assertThat(CursorCodec.decode(null)).isNull();
        assertThat(CursorCodec.decode(" ")).isNull();
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> CursorCodec.decode("bm90LWEtY3Vyc29y")).isInstanceOf(IllegalArgumentException.class);
    }
}
