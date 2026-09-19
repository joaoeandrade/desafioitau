package com.example.eventplatform.adapter.out.kafka;

import com.example.eventplatform.config.PlatformProperties;
import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.port.out.EventLogPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publica no Kafka com produtor idempotente (enable.idempotence=true, acks=all).
 *
 * <p>Chave de particionamento = tenant + origem: preserva a ordem dos eventos de uma mesma
 * origem e distribui carga entre partições mesmo quando um único tenant domina o tráfego.
 */
@Component
public class KafkaEventLogPublisher implements EventLogPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EventMessageCodec codec;
    private final String ingestedTopic;
    private final String processedTopic;

    public KafkaEventLogPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                  EventMessageCodec codec,
                                  PlatformProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.codec = codec;
        this.ingestedTopic = props.kafka().ingestedTopic();
        this.processedTopic = props.kafka().processedTopic();
    }

    @Override
    public CompletableFuture<Void> publishAccepted(Event event) {
        return send(ingestedTopic, event);
    }

    @Override
    public CompletableFuture<Void> publishProcessed(Event event) {
        return send(processedTopic, event);
    }

    private CompletableFuture<Void> send(String topic, Event event) {
        return kafkaTemplate.send(topic, partitionKey(event), codec.encode(event))
                .thenApply(result -> null);
    }

    static String partitionKey(Event event) {
        return event.tenantId() + "|" + event.source();
    }
}
