package com.example.eventplatform.adapter.in.kafka;

import com.example.eventplatform.adapter.out.kafka.EventMessageCodec;
import com.example.eventplatform.application.IntegrationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Grupo de consumo independente: integrações lentas acumulam lag apenas aqui,
 * sem afetar a persistência nem a API de ingestão (isolamento por bulkhead via Kafka).
 */
@Component
public class IntegrationConsumer {

    private final IntegrationService integrationService;
    private final EventMessageCodec codec;

    public IntegrationConsumer(IntegrationService integrationService, EventMessageCodec codec) {
        this.integrationService = integrationService;
        this.codec = codec;
    }

    @KafkaListener(
            id = "event-integration",
            groupId = "event-integration",
            topics = "${platform.kafka.processed-topic}",
            containerFactory = "recordListenerFactory",
            concurrency = "${platform.kafka.integration-concurrency}",
            autoStartup = "${platform.integration.enabled}")
    public void onEvent(ConsumerRecord<String, String> record) {
        // IllegalArgumentException (mensagem inválida) não é retentada: vai direto para a DLT.
        integrationService.dispatch(List.of(codec.decode(record.value())));
    }
}
