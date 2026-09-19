package com.example.eventplatform.adapter.out.cache;

import com.example.eventplatform.config.PlatformProperties;
import com.example.eventplatform.domain.port.out.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * SET NX com TTL: operação O(1), ~0,2 ms no Redis. Falha aberta: se o Redis estiver fora,
 * aceitamos o evento e a deduplicação final acontece no banco (ON CONFLICT DO NOTHING).
 * Assim, a indisponibilidade do cache degrada a resposta a reenvios, nunca a ingestão.
 */
@Component
@ConditionalOnProperty(name = "platform.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final String prefix;
    private final Counter failOpen;

    public RedisIdempotencyStore(StringRedisTemplate redis, PlatformProperties props, MeterRegistry registry) {
        this.redis = redis;
        this.ttl = props.idempotency().ttl();
        this.prefix = props.idempotency().keyPrefix();
        this.failOpen = Counter.builder("idempotency.fail_open")
                .description("Verificações de idempotência ignoradas por indisponibilidade do Redis")
                .register(registry);
    }

    @Override
    public boolean registerIfAbsent(UUID eventId) {
        try {
            Boolean created = redis.opsForValue().setIfAbsent(prefix + eventId, "1", ttl);
            return !Boolean.FALSE.equals(created);
        } catch (RuntimeException e) {
            failOpen.increment();
            log.debug("redis indisponível, seguindo sem checagem de idempotência: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void release(UUID eventId) {
        try {
            redis.delete(prefix + eventId);
        } catch (RuntimeException e) {
            log.debug("falha ao liberar chave de idempotência {}: {}", eventId, e.getMessage());
        }
    }
}
