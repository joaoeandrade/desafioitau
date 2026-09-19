package com.example.eventplatform.adapter.out.integration;

import com.example.eventplatform.config.PlatformProperties;
import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.port.out.DownstreamNotifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.LinkedHashMap;

/**
 * Entrega eventos a um sistema externo via HTTP.
 *
 * <p>Camadas de resiliência (ordem de execução do Resilience4j: Retry → CircuitBreaker → Bulkhead):
 * <ul>
 *   <li><b>Bulkhead</b>: limita chamadas concorrentes, evitando esgotar threads/conexões.</li>
 *   <li><b>CircuitBreaker</b>: abre ao detectar taxa de erro/lentidão e falha rápido.</li>
 *   <li><b>Retry</b>: poucas tentativas curtas para erros transitórios; retentativas longas ficam
 *       a cargo do backoff do consumidor Kafka, que não perde a mensagem.</li>
 * </ul>
 * O header {@code Idempotency-Key} permite ao receptor descartar entregas repetidas.
 */
@Component
@ConditionalOnProperty(name = "platform.integration.enabled", havingValue = "true")
public class WebhookDownstreamNotifier implements DownstreamNotifier {

    private static final String RESILIENCE_NAME = "downstream";

    private final RestClient client;
    private final URI webhookUrl;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public WebhookDownstreamNotifier(RestClient.Builder builder, ObjectMapper objectMapper, PlatformProperties props) {
        var integration = props.integration();
        if (integration.webhookUrl() == null) {
            throw new IllegalStateException("platform.integration.webhook-url é obrigatório com a integração habilitada");
        }
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(integration.connectTimeout())
                .version(HttpClient.Version.HTTP_2)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(integration.readTimeout());

        this.client = builder.requestFactory(requestFactory).build();
        this.webhookUrl = integration.webhookUrl();
        this.objectMapper = objectMapper;
        this.secret = integration.webhookSecret() == null || integration.webhookSecret().isBlank() ? null
                : integration.webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Retry(name = RESILIENCE_NAME)
    @CircuitBreaker(name = RESILIENCE_NAME)
    @Bulkhead(name = RESILIENCE_NAME)
    public void notify(Event event) {
        String body = toJson(event);
        var request = client.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", event.id().toString())
                .header("X-Event-Type", event.type());
        if (secret != null) {
            request = request.header("X-Signature", "sha256=" + hmac(body));
        }
        request.body(body).retrieve().toBodilessEntity();
    }

    private String toJson(Event e) {
        try {
            var envelope = new LinkedHashMap<String, Object>();
            envelope.put("id", e.id());
            envelope.put("tenantId", e.tenantId());
            envelope.put("type", e.type());
            envelope.put("source", e.source());
            envelope.put("occurredAt", e.occurredAt());
            envelope.put("data", objectMapper.readTree(e.payload()));
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("payload inválido no evento " + e.id(), ex);
        }
    }

    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
