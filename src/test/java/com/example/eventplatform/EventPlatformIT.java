package com.example.eventplatform;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Teste ponta a ponta com infraestrutura real (PostgreSQL, Kafka, Redis em containers).
 * Executar com: {@code mvn verify -Pintegration} (requer Docker).
 */
@Testcontainers
@ActiveProfiles("local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "platform.kafka.partitions=3"})
class EventPlatformIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.9.1");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    private HttpHeaders headers;

    @BeforeEach
    void authenticate() {
        @SuppressWarnings("unchecked")
        Map<String, String> token = rest.postForObject("/dev/token?tenant=tenant-it", null, Map.class);
        headers = new HttpHeaders();
        headers.setBearerAuth(token.get("access_token"));
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void ingestsPersistsAndServesEvent() {
        String key = UUID.randomUUID().toString();
        headers.set("Idempotency-Key", key);
        var body = "{\"type\":\"order.created\",\"source\":\"it\",\"payload\":{\"total\":42}}";

        var accepted = rest.exchange("/api/v1/events", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String id = (String) accepted.getBody().get("eventId");

        // Reenvio com a mesma chave: mesmo id, sem duplicar.
        var duplicate = rest.exchange("/api/v1/events", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicate.getBody().get("eventId")).isEqualTo(id);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var found = rest.exchange("/api/v1/events/" + id, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(found.getBody().get("status")).isEqualTo("PROCESSED");
        });

        Integer rows = jdbc.queryForObject("SELECT count(*) FROM events WHERE id = ?::uuid", Integer.class, id);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void otherTenantCannotReadEvent() {
        var body = "{\"type\":\"order.created\",\"source\":\"it\",\"payload\":{}}";
        var accepted = rest.exchange("/api/v1/events", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        String id = (String) accepted.getBody().get("eventId");

        @SuppressWarnings("unchecked")
        Map<String, String> other = rest.postForObject("/dev/token?tenant=intruder", null, Map.class);
        var otherHeaders = new HttpHeaders();
        otherHeaders.setBearerAuth(other.get("access_token"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE id = ?::uuid", Integer.class, id))
                        .isEqualTo(1));

        var response = rest.exchange("/api/v1/events/" + id, HttpMethod.GET, new HttpEntity<>(otherHeaders), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
