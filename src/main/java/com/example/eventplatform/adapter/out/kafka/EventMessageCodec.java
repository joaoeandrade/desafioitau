package com.example.eventplatform.adapter.out.kafka;

import com.example.eventplatform.domain.model.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Serialização explícita (String JSON) em vez de JsonSerializer com headers de tipo:
 * consumidores em outras linguagens leem o tópico sem conhecer classes Java.
 */
@Component
public class EventMessageCodec {

    private final ObjectMapper objectMapper;

    public EventMessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Event event) {
        try {
            return objectMapper.writeValueAsString(EventMessage.from(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("falha ao serializar evento " + event.id(), e);
        }
    }

    /** @throws IllegalArgumentException se a mensagem estiver malformada ou violar o domínio. */
    public Event decode(String json) {
        try {
            return objectMapper.readValue(json, EventMessage.class).toDomain();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("mensagem malformada: " + e.getOriginalMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("mensagem inválida: " + e.getMessage(), e);
        }
    }
}
