-- =====================================================================
-- Tabela de eventos particionada por mês (occurred_at).
--
-- Por que particionar?
--  * 5k ev/s ≈ 430M linhas/dia em regime normal. Partições mantêm índices pequenos
--    (cabem em memória) e permitem expurgo por DROP PARTITION (O(1), sem VACUUM pesado).
--  * Consultas por janela de tempo fazem partition pruning.
--
-- Em produção, recomenda-se pg_partman para criar partições futuras automaticamente;
-- aqui a função create_events_partition() + job agendado cumprem esse papel.
-- =====================================================================

CREATE TABLE events (
    id           UUID        NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL,
    type         VARCHAR(128) NOT NULL,
    source       VARCHAR(128) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL,
    persisted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload      JSONB       NOT NULL,
    status       VARCHAR(16) NOT NULL,
    -- A chave de partição precisa fazer parte da PK em tabelas particionadas.
    -- (id, occurred_at) é estável entre reentregas, garantindo a idempotência.
    CONSTRAINT pk_events PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Consulta principal: eventos de um tenant em ordem cronológica reversa (keyset pagination).
CREATE INDEX ix_events_tenant_time ON events (tenant_id, occurred_at DESC, id DESC);
-- Filtro adicional por tipo.
CREATE INDEX ix_events_tenant_type_time ON events (tenant_id, type, occurred_at DESC);

-- Partição padrão: captura eventos fora das janelas criadas (ex.: eventos muito antigos).
CREATE TABLE events_default PARTITION OF events DEFAULT;

CREATE OR REPLACE FUNCTION create_events_partition(p_month DATE)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::DATE;
    v_name  TEXT := format('events_%s', to_char(v_start, 'YYYY_MM'));
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF events FOR VALUES FROM (%L) TO (%L)',
        v_name, v_start, v_end);
END;
$$;

-- Cria partições do mês anterior até 12 meses à frente.
DO $$
DECLARE
    m INT;
BEGIN
    FOR m IN -1..12 LOOP
        PERFORM create_events_partition((date_trunc('month', now()) + make_interval(months => m))::DATE);
    END LOOP;
END;
$$;

-- Ajustes de autovacuum para tabela de escrita intensiva e majoritariamente append-only.
ALTER TABLE events_default SET (autovacuum_vacuum_scale_factor = 0.05, autovacuum_analyze_scale_factor = 0.02);
