package com.example.eventplatform.domain.model;

import java.util.List;

public record EventPage(List<Event> items, EventQuery.Cursor nextCursor) {

    public EventPage {
        items = List.copyOf(items);
    }

    public boolean hasMore() {
        return nextCursor != null;
    }
}
