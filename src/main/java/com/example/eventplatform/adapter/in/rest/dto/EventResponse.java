package com.example.eventplatform.adapter.in.rest.dto;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String type,
        String source,
        Instant occurredAt,
        Instant receivedAt,
        EventStatus status,
        @JsonRawValue String payload) {

    public static EventResponse of(Event e) {
        return new EventResponse(e.id(), e.type(), e.source(), e.occurredAt(), e.receivedAt(), e.status(), e.payload());
    }

    public record Page(List<EventResponse> items, String nextCursor) {
    }
}
