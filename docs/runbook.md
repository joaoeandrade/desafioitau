# Runbook operacional

## Latência de ingestão
**Sintoma**: alerta `IngestLatencyP95Above200ms`.
1. Verifique a saúde do Kafka (latência de produção, ISR encolhendo, broker fora). A ingestão depende
   apenas dele e do Redis.
2. Verifique CPU dos pods e se o HPA está no máximo → aumente `maxReplicas` ou o tamanho do nó.
3. Verifique pausas de GC (`jvm_gc_pause_seconds`). Com ZGC devem ser < 1 ms.
4. Redis lento? O timeout é de 50 ms e falha aberto — veja `idempotency_fail_open_total`.

## Erros de ingestão
**Sintoma**: `IngestErrorRateHigh` (5xx).
- 503 com `Retry-After`: o broker não confirmou em 150 ms. Causa quase sempre no Kafka.
- 500: procure por `erro inesperado` nos logs filtrando pelo `traceId`.

## Lag de consumo
**Sintoma**: `PersistenceConsumerLagGrowing`.
1. Duração do lote (`events_persistence_batch_duration_seconds`) subiu? → banco lento: locks, autovacuum,
   I/O saturado, pool esgotado (`hikaricp_connections_pending`).
2. Consumidores < partições? → aumente réplicas ou `PERSISTENCE_CONCURRENCY` (teto: 24 no total).
3. Lag cai sozinho após picos — só aja se crescer de forma sustentada.

## DLT
Mensagens em `*.dlt` não foram processadas após as retentativas.
1. Inspecione: `kafka-console-consumer --topic events.ingested.v1.dlt --property print.headers=true`
   (os headers `kafka_dlt-exception-*` trazem a causa).
2. Corrija a causa e reprocesse republicando no tópico original (a persistência é idempotente).

## Integração externa
**Sintoma**: `DownstreamCircuitOpen`.
- O sistema externo está falhando ou lento. Nada é perdido: o grupo `event-integration` acumula lag e
  volta a entregar quando o circuito fecha. Após o backoff máximo (2 min), mensagens vão para a DLT.

## Redis indisponível
- Sem impacto em disponibilidade. Reenvios do cliente deixam de receber 200/DUPLICATE (recebem 202),
  e o banco descarta a duplicata. Cache de leitura também é ignorado (mais carga no banco).
