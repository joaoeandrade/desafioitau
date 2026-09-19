package com.example.eventplatform.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchIngestRequest(
        @NotEmpty @Size(max = 500) List<@Valid IngestEventRequest> events) {
}
