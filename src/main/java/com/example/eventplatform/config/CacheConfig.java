package com.example.eventplatform.config;

import com.example.eventplatform.domain.model.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Serializador tipado: records são final, então não dependemos de default typing do Jackson. */
    @Bean
    RedisCacheManagerBuilderCustomizer eventsCacheCustomizer(ObjectMapper objectMapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Event.class);
        return builder -> builder.withCacheConfiguration("events",
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(10))
                        .disableCachingNullValues()
                        .prefixCacheNameWith("ep:cache:")
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer)));
    }

    /** Falhas do cache nunca devem derrubar a leitura: registra e segue para o banco. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("cache get falhou cache={} key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("cache put falhou cache={} key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("cache evict falhou cache={} key={}: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("cache clear falhou cache={}: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
