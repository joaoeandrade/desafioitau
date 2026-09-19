package com.example.eventplatform.config;

import com.example.eventplatform.application.IngestEventService;
import com.example.eventplatform.application.IntegrationService;
import com.example.eventplatform.application.ProcessEventsService;
import com.example.eventplatform.application.QueryEventsService;
import com.example.eventplatform.domain.port.in.IngestEventUseCase;
import com.example.eventplatform.domain.port.in.ProcessEventsUseCase;
import com.example.eventplatform.domain.port.in.QueryEventsUseCase;
import com.example.eventplatform.domain.port.out.DownstreamNotifier;
import com.example.eventplatform.domain.port.out.EventLogPublisher;
import com.example.eventplatform.domain.port.out.EventRepository;
import com.example.eventplatform.domain.port.out.IdempotencyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Composição dos casos de uso. O núcleo (domain + application) não conhece Spring;
 * aqui é o único ponto onde portas e adapters são conectados.
 */
@Configuration(proxyBeanMethods = false)
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IngestEventUseCase ingestEventUseCase(EventLogPublisher publisher, IdempotencyStore store,
                                          Clock clock, PlatformProperties props) {
        var ingestion = props.ingestion();
        return new IngestEventService(publisher, store, clock,
                ingestion.publishTimeout(), ingestion.maxClockSkew(), ingestion.maxBatchSize());
    }

    @Bean
    ProcessEventsUseCase processEventsUseCase(EventRepository repository, EventLogPublisher publisher) {
        return new ProcessEventsService(repository, publisher);
    }

    @Bean
    QueryEventsUseCase queryEventsUseCase(EventRepository repository) {
        return new QueryEventsService(repository);
    }

    @Bean
    IntegrationService integrationService(DownstreamNotifier notifier, PlatformProperties props) {
        return new IntegrationService(notifier, props.integration().subscribedTypes());
    }
}
