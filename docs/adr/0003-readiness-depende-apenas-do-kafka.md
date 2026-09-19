# ADR-0003 — Readiness depende apenas do Kafka

**Status**: aceito

## Contexto
Incluir banco e Redis no readiness probe parece seguro, mas uma falha do PostgreSQL tiraria **todos** os
pods do balanceador ao mesmo tempo, derrubando também a ingestão — que não depende do banco.

## Decisão
- `readiness` = `readinessState` + `kafka` (indicador próprio via AdminClient).
- `liveness` = apenas `livenessState` (nunca dependências externas, para evitar reinícios em cascata).
- Saúde do Redis desligada (fail-open).

## Consequências
- (+) Durante incidentes no banco, a API continua aceitando eventos; o backlog fica no Kafka.
- (−) Consultas (`GET`) retornam erro durante a falha do banco — degradação parcial e explícita, monitorada por alertas.
