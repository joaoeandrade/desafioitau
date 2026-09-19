package com.example.eventplatform.adapter.in.kafka;

import com.example.eventplatform.adapter.out.kafka.EventMessageCodec;
import com.example.eventplatform.config.KafkaConfig;
import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.port.in.ProcessEventsUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consome o tópico de eventos aceitos em lotes (max.poll.records) e delega a persistência.
 * Mensagens malformadas ("poison pills") são desviadas para a DLT imediatamente,
 * sem bloquear o restante do lote.
 */
@Component
public class PersistenceConsumer {

    private static final Logger log = LoggerFactory.getLogger(PersistenceConsumer.class);

    private final ProcessEventsUseCase processEvents;
    private final EventMessageCodec codec;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Timer batchTimer;
    private final DistributionSummary batchSize;
    private final Counter persisted;
    private final Counter poison;

    public PersistenceConsumer(ProcessEventsUseCase processEvents,
                               EventMessageCodec codec,
                               KafkaTemplate<String, String> kafkaTemplate,
                               MeterRegistry registry) {
        this.processEvents = processEvents;
        this.codec = codec;
        this.kafkaTemplate = kafkaTemplate;
        this.batchTimer = Timer.builder("events.persistence.batch.duration")
                .description("Tempo para persistir e republicar um lote")
                .publishPercentileHistogram()
                .register(registry);
        this.batchSize = DistributionSummary.builder("events.persistence.batch.size")
                .description("Tamanho dos lotes consumidos")
                .register(registry);
        this.persisted = Counter.builder("events.persisted").register(registry);
        this.poison = Counter.builder("events.poison").description("Mensagens inválidas enviadas à DLT")
                .register(registry);
    }

    @KafkaListener(
            id = "event-persistence",
            groupId = "event-persistence",
            topics = "${platform.kafka.ingested-topic}",
            containerFactory = "batchListenerFactory",
            concurrency = "${platform.kafka.persistence-concurrency}")
    public void onBatch(List<ConsumerRecord<String, String>> records) {
        List<Event> events = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            try {
                events.add(codec.decode(record.value()));
            } catch (IllegalArgumentException e) {
                poison.increment();
                log.warn("mensagem inválida desviada para DLT topic={} partition={} offset={} motivo={}",
                        record.topic(), record.partition(), record.offset(), e.getMessage());
                kafkaTemplate.send(record.topic() + KafkaConfig.DLT_SUFFIX, record.partition(),
                        record.key(), record.value());
            }
        }

        batchSize.record(records.size());
        batchTimer.record(() -> {
            var report = processEvents.process(events);
            persisted.increment(report.persisted());
            log.debug("lote processado {}", report);
        });
    }
}
