# ADR-0005 — JDBC com escrita em lote em vez de JPA

**Status**: aceito

## Contexto
O caminho de escrita insere milhares de linhas por segundo, sem atualizações nem relacionamentos.

## Decisão
`JdbcTemplate.batchUpdate` + `reWriteBatchedInserts=true` + uma transação por lote. Modelo imutável,
mapeamento manual em um único lugar (`JdbcEventRepository`).

## Consequências
- (+) Menor uso de CPU/memória e controle total do SQL (`ON CONFLICT`, `jsonb`, keyset pagination).
- (+) Sem surpresas de ORM (N+1, flush implícito, dirty checking).
- (−) Mapeamento manual; aceitável pelo tamanho reduzido do modelo.
