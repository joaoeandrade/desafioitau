package com.example.eventplatform.adapter.in.rest;

import com.example.eventplatform.application.EventLogUnavailableException;
import com.example.eventplatform.config.SecurityConfig;
import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventPage;
import com.example.eventplatform.domain.port.in.IngestEventUseCase;
import com.example.eventplatform.domain.port.in.IngestEventUseCase.IngestResult;
import com.example.eventplatform.domain.port.in.QueryEventsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class EventControllerTest {

    private static final String BODY = """
            {"type":"order.created","source":"checkout","payload":{"total":10}}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    IngestEventUseCase ingest;

    @MockitoBean
    QueryEventsUseCase query;

    @MockitoBean
    IngestMetrics metrics;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor writer(String tenant) {
        return jwt().jwt(j -> j.claim("tenant_id", tenant)).authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_events:write"));
    }

    private static JwtRequestPostProcessor reader(String tenant) {
        return jwt().jwt(j -> j.claim("tenant_id", tenant)).authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_events:read"));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutWriteScope() throws Exception {
        mvc.perform(post("/api/v1/events").with(reader("t1")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsEventAndUsesTenantFromToken() throws Exception {
        UUID id = UUID.randomUUID();
        given(ingest.ingest(any())).willReturn(IngestResult.accepted(id));

        mvc.perform(post("/api/v1/events").with(writer("tenant-a"))
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(id.toString()))
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(header().exists("X-Correlation-Id"));

        org.mockito.Mockito.verify(ingest).ingest(org.mockito.ArgumentMatchers.argThat(c ->
                c.tenantId().equals("tenant-a") && c.idempotencyKey().equals("abc-123")));
    }

    @Test
    void duplicateReturns200() throws Exception {
        given(ingest.ingest(any())).willReturn(IngestResult.duplicate(UUID.randomUUID()));

        mvc.perform(post("/api/v1/events").with(writer("t1")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
    }

    @Test
    void validatesRequestBody() throws Exception {
        mvc.perform(post("/api/v1/events").with(writer("t1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"source\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenWithoutTenantIsForbidden() throws Exception {
        mvc.perform(post("/api/v1/events")
                        .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_events:write")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void brokerUnavailableReturns503WithRetryAfter() throws Exception {
        given(ingest.ingest(any())).willThrow(new EventLogUnavailableException("timeout", null));

        mvc.perform(post("/api/v1/events").with(writer("t1")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"));
    }

    @Test
    void partialBatchReturns207() throws Exception {
        given(ingest.ingestBatch(anyList())).willReturn(List.of(
                IngestResult.accepted(UUID.randomUUID()),
                IngestResult.rejected(null, "type inválido")));

        String batch = """
                {"events":[
                  {"type":"order.created","source":"checkout","payload":{}},
                  {"type":"order.created","source":"checkout","payload":{}}
                ]}
                """;
        mvc.perform(post("/api/v1/events/batch").with(writer("t1")).contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));
    }

    @Test
    void returns404ForUnknownOrForeignEvent() throws Exception {
        UUID id = UUID.randomUUID();
        given(query.findById(eq("t1"), eq(id))).willReturn(Optional.empty());

        mvc.perform(get("/api/v1/events/{id}", id).with(reader("t1")))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsEventWithRawJsonPayload() throws Exception {
        var now = Instant.parse("2026-11-03T12:00:00Z");
        var event = Event.accepted(UUID.randomUUID(), "t1", "order.created", "checkout", now, now, "{\"total\":10}")
                .markProcessed();
        given(query.findById("t1", event.id())).willReturn(Optional.of(event));

        mvc.perform(get("/api/v1/events/{id}", event.id()).with(reader("t1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.total").value(10))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void searchReturnsPage() throws Exception {
        given(query.search(any())).willReturn(new EventPage(List.of(), null));

        mvc.perform(get("/api/v1/events").with(reader("t1")).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void invalidCursorIs400() throws Exception {
        mvc.perform(get("/api/v1/events").with(reader("t1")).param("cursor", "!!!"))
                .andExpect(status().isBadRequest());
    }
}
