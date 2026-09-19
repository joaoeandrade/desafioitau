package com.example.eventplatform.domain.model;

public enum EventStatus {
    /** Recebido pela API e publicado de forma durável no log (Kafka). */
    ACCEPTED,
    /** Persistido e disponível para consulta e integrações. */
    PROCESSED
}
