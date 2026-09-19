package com.example.eventplatform.adapter.out.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Garante partições futuras. Idempotente (CREATE TABLE IF NOT EXISTS) e seguro com várias
 * réplicas rodando ao mesmo tempo graças ao advisory lock transacional — apenas uma executa
 * por vez, e o lock é liberado no commit (sem risco de vazar em conexões do pool).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "platform.partitions.maintenance-enabled", havingValue = "true", matchIfMissing = true)
public class PartitionMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(PartitionMaintenanceJob.class);
    private static final long LOCK_KEY = 7_420_001L;
    private static final int MONTHS_AHEAD = 3;

    private final JdbcTemplate jdbc;

    public PartitionMaintenanceJob(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void ensureFuturePartitions() {
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }
        LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        for (int i = 0; i <= MONTHS_AHEAD; i++) {
            jdbc.query("SELECT create_events_partition(?::date)", (ResultSetExtractor<Void>) rs -> null,
                    month.plusMonths(i).toString());
        }
        log.info("partições garantidas até {}", month.plusMonths(MONTHS_AHEAD));
    }
}
