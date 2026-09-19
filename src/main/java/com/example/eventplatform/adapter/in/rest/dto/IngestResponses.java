package com.example.eventplatform.adapter.in.rest.dto;

import com.example.eventplatform.domain.port.in.IngestEventUseCase.IngestResult;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.Outcome;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

public final class IngestResponses {

    private IngestResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemResult(int index, UUID eventId, Outcome outcome, String error) {
        public static ItemResult of(int index, IngestResult r) {
            return new ItemResult(index, r.eventId(), r.outcome(), r.error());
        }
    }

    public record Single(UUID eventId, Outcome outcome) {
        public static Single of(IngestResult r) {
            return new Single(r.eventId(), r.outcome());
        }
    }

    public record Batch(int accepted, int duplicates, int rejected, int retry, List<ItemResult> results) {
        public static Batch of(List<IngestResult> results) {
            int a = 0, d = 0, rej = 0, rt = 0;
            var items = new java.util.ArrayList<ItemResult>(results.size());
            for (int i = 0; i < results.size(); i++) {
                var r = results.get(i);
                switch (r.outcome()) {
                    case ACCEPTED -> a++;
                    case DUPLICATE -> d++;
                    case REJECTED -> rej++;
                    case RETRY -> rt++;
                }
                items.add(ItemResult.of(i, r));
            }
            return new Batch(a, d, rej, rt, items);
        }

        public boolean allSucceeded() {
            return rejected == 0 && retry == 0;
        }
    }
}
