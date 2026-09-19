package com.example.eventplatform.adapter.in.rest;

import com.example.eventplatform.domain.model.EventQuery;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Cursor opaco para o cliente: base64url("epochSeconds.nanos|uuid"). */
final class CursorCodec {

    private CursorCodec() {
    }

    static String encode(EventQuery.Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        String raw = cursor.occurredAt().getEpochSecond() + "." + cursor.occurredAt().getNano() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static EventQuery.Cursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int bar = raw.indexOf('|');
            String[] ts = raw.substring(0, bar).split("\\.");
            Instant instant = Instant.ofEpochSecond(Long.parseLong(ts[0]), Long.parseLong(ts[1]));
            return new EventQuery.Cursor(instant, UUID.fromString(raw.substring(bar + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cursor inválido");
        }
    }
}
