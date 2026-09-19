package com.example.eventplatform.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

    public static final String DLT_SUFFIX = ".dlt";

    // ---------- Tópicos ----------
    // Partições definem o teto de paralelismo dos consumidores. 24 partições suportam até
    // 24 consumidores ativos por grupo — margem para ~4x o pico previsto (ver docs/capacity-planning.md).

    @Bean
    NewTopic ingestedTopic(PlatformProperties props) {
        return topic(props.kafka().ingestedTopic(), props);
    }

    @Bean
    NewTopic ingestedDltTopic(PlatformProperties props) {
        return topic(props.kafka().ingestedTopic() + DLT_SUFFIX, props);
    }

    @Bean
    NewTopic processedTopic(PlatformProperties props) {
        return topic(props.kafka().processedTopic(), props);
    }

    @Bean
    NewTopic processedDltTopic(PlatformProperties props) {
        return topic(props.kafka().processedTopic() + DLT_SUFFIX, props);
    }

    private static NewTopic topic(String name, PlatformProperties props) {
        return TopicBuilder.name(name)
                .partitions(props.kafka().partitions())
                .replicas(props.kafka().replicas())
                .config("min.insync.replicas", props.kafka().replicas() > 1 ? "2" : "1")
                .config("retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000))
                .config("compression.type", "producer")
                .build();
    }

    // ---------- Tratamento de erros ----------

    @Bean
    DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<String, String> template) {
        return new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
    }

    /**
     * Persistência em lote: retenta o lote inteiro algumas vezes (falhas típicas são transitórias:
     * failover do banco, pool esgotado). Esgotadas as tentativas, cada registro vai para a DLT
     * — o consumo nunca fica travado indefinidamente.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> batchListenerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DeadLetterPublishingRecoverer recoverer) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setBatchListener(true);
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 5)));
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    /**
     * Integrações: processamento por registro com backoff exponencial (o circuit breaker do
     * Resilience4j corta chamadas enquanto o sistema externo está fora; o backoff dá tempo
     * para ele se recuperar antes de mandar a mensagem para a DLT).
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<Object, Object> recordListenerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            DeadLetterPublishingRecoverer recoverer) {
        var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
        configurer.configure(factory, consumerFactory);
        factory.setBatchListener(false);
        var backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(120_000L);
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }

    // ---------- Admin (health check) ----------

    @Bean(destroyMethod = "close")
    AdminClient kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }
}
