package com.example.eventplatform.observability;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * A ingestão depende somente do Kafka. Por isso o probe de readiness considera o Kafka,
 * mas NÃO o banco nem o Redis: se o PostgreSQL ficar fora, a API continua aceitando eventos
 * (eles se acumulam no Kafka) em vez de todos os pods saírem do balanceador de uma vez.
 */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator {

    private final AdminClient adminClient;

    public KafkaHealthIndicator(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    @Override
    public Health health() {
        try {
            var cluster = adminClient.describeCluster(new DescribeClusterOptions().timeoutMs(2_000));
            int nodes = cluster.nodes().get(3, TimeUnit.SECONDS).size();
            String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);
            return Health.up().withDetail("clusterId", clusterId).withDetail("brokers", nodes).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down(e).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getClass().getSimpleName()).build();
        }
    }
}
