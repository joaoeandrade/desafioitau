package com.example.eventplatform.adapter.out.persistence;

import com.example.eventplatform.domain.model.Event;
import com.example.eventplatform.domain.model.EventPage;
import com.example.eventplatform.domain.model.EventQuery;
import com.example.eventplatform.domain.model.EventStatus;
import com.example.eventplatform.domain.port.out.EventRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC puro em vez de JPA: no caminho de escrita em lote, ORM adiciona overhead (dirty checking,
 * contexto de persistência) sem benefício. Com {@code reWriteBatchedInserts=true} o driver do
 * PostgreSQL transforma o batch em INSERTs multi-linha, reduzindo drasticamente os round-trips.
 */
@Repository
public class JdbcEventRepository implements EventRepository {

    static final String CACHE_NAME = "events";

    private static final String INSERT_SQL = """
            INSERT INTO events (id, tenant_id, type, source, occurred_at, received_at, payload, status)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            ON CONFLICT (id, occurred_at) DO NOTHING
            """;

    private static final String SELECT_COLUMNS =
            "SELECT id, tenant_id, type, source, occurred_at, received_at, payload::text AS payload, status FROM events";

    private static final RowMapper<Event> ROW_MAPPER = (rs, rowNum) -> new Event(
            rs.getObject("id", UUID.class),
            rs.getString("tenant_id"),
            rs.getString("type"),
            rs.getString("source"),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getTimestamp("received_at").toInstant(),
            rs.getString("payload"),
            EventStatus.valueOf(rs.getString("status")));

    private final JdbcTemplate jdbc;
    private final int batchSize;

    public JdbcEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.batchSize = 500;
    }

    /** Uma transação por lote: um único commit (fsync do WAL) para centenas de linhas. */
    @Override
    @Transactional
    public int saveAll(List<Event> events) {
        if (events.isEmpty()) {
            return 0;
        }
        int[][] counts = jdbc.batchUpdate(INSERT_SQL, events, batchSize, (ps, e) -> {
            ps.setObject(1, e.id());
            ps.setString(2, e.tenantId());
            ps.setString(3, e.type());
            ps.setString(4, e.source());
            ps.setTimestamp(5, Timestamp.from(e.occurredAt()));
            ps.setTimestamp(6, Timestamp.from(e.receivedAt()));
            ps.setString(7, e.payload());
            ps.setString(8, e.status().name());
        });
        return countInserted(counts, events.size());
    }

    static int countInserted(int[][] counts, int sent) {
        int inserted = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                if (c == Statement.SUCCESS_NO_INFO) {
                    return sent; // driver com rewrite não informa linhas afetadas por item
                }
                inserted += Math.max(c, 0);
            }
        }
        return inserted;
    }

    /**
     * Eventos persistidos são imutáveis, então o cache não precisa de invalidação — só de TTL
     * para limitar memória. Resultados vazios não são cacheados (evento pode estar a caminho).
     */
    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "#id", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<Event> findById(UUID id) {
        return jdbc.query(SELECT_COLUMNS + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public EventPage search(EventQuery q) {
        var sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(q.tenantId());

        if (q.type() != null) {
            sql.append(" AND type = ?");
            args.add(q.type());
        }
        if (q.from() != null) {
            sql.append(" AND occurred_at >= ?");
            args.add(Timestamp.from(q.from()));
        }
        if (q.to() != null) {
            sql.append(" AND occurred_at < ?");
            args.add(Timestamp.from(q.to()));
        }
        if (q.after() != null) {
            sql.append(" AND (occurred_at, id) < (?, ?)");
            args.add(Timestamp.from(q.after().occurredAt()));
            args.add(q.after().id());
        }
        // Busca limit+1 para saber se existe próxima página sem COUNT(*).
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(q.limit() + 1);

        List<Event> rows = jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
        if (rows.size() <= q.limit()) {
            return new EventPage(rows, null);
        }
        List<Event> page = rows.subList(0, q.limit());
        Event last = page.getLast();
        return new EventPage(page, new EventQuery.Cursor(last.occurredAt(), last.id()));
    }
}
