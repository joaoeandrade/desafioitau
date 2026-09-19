# Planejamento de capacidade

Premissas: payload médio de 1 KiB, 50 % das requisições pelo endpoint unitário e 50 % em lotes de 100.
Os números abaixo são **estimativas de dimensionamento inicial** — devem ser confirmados com
`loadtest/ingest.js` no ambiente alvo antes de ir para produção.

## Vazão

| | Regime normal | Pico |
|---|---|---|
| Eventos/s | 5.000 | 20.000 |
| Banda de entrada | ~5 MB/s | ~20 MB/s |
| Escrita no Kafka (RF=3) | ~15 MB/s no cluster | ~60 MB/s no cluster |
| Volume diário (se sustentado) | ~430 M eventos / ~430 GB | — |

## API (pods)

- Custo estimado por evento no caminho de ingestão: 0,2–0,4 ms de CPU (parse JSON, validação, serialização,
  produtor Kafka). O núcleo de domínio isolado processa >300 mil eventos/s em uma thread — o custo
  dominante é I/O e serialização, não regra de negócio.
- Meta: manter CPU ≤ 60 % para preservar a cauda de latência → ~1.500–2.500 ev/s por pod com 2 vCPUs.
- **Regime normal**: 4 pods (mínimo do HPA; sobrevive à perda de uma zona).
- **Pico**: 8–12 pods; `maxReplicas: 16` dá ~30 % de margem adicional.
- O HPA dobra réplicas a cada 30 s; enquanto escala, o Kafka absorve e a latência é protegida pelo
  `publish-timeout` de 150 ms (o cliente recebe 503 + `Retry-After` em vez de esperar indefinidamente).

## Kafka

- 24 partições por tópico: teto de 24 consumidores por grupo e ~1 MB/s por partição no pico — confortável.
- 3 brokers, RF=3, `min.insync.replicas=2`: tolera a perda de 1 broker sem indisponibilidade de escrita.
- Retenção de 7 dias: permite reprocessar ou reconstruir o banco a partir do log.
- Produtor: `lz4` reduz ~60–70 % de payload JSON; `linger.ms=5` + `batch.size=64KiB` aumentam o lote por request.

## PostgreSQL

- Escrita em lote: 20k ev/s ≈ 20 MB/s de dados + índices + WAL. Instância de referência: 8 vCPU / 32 GiB,
  SSD provisionado (≥ 10k IOPS), `max_wal_size` alto para reduzir checkpoints.
- Se o banco não acompanhar o pico, **o lag cresce no Kafka e a API não é afetada**; após o pico, o
  backlog drena. Exemplo: com capacidade de escrita de 12k ev/s, 5 minutos de pico a 20k ev/s acumulam
  ~2,4 M de mensagens, drenadas em ~6 minutos após a volta ao regime normal (7k ev/s de folga).
- Conexões: 10 por pod × 16 pods = 160. Acima disso, PgBouncer em modo *transaction*.
- Crescimento: partições mensais; política de retenção definida pelo negócio (ex.: 90 dias quentes,
  depois exportação para object storage via Kafka Connect/S3 Sink).

## Redis

- Só eventos enviados **com** `Idempotency-Key` geram chave no Redis (sem chave o id é aleatório e não há
  reenvio a detectar). No pior caso — 100 % dos eventos com chave, 5k ev/s, TTL de 24 h — são ~430 M chaves
  × ~80 bytes ≈ 35 GB. Ajuste `platform.idempotency.ttl` à janela real de reenvio dos clientes
  (1–6 h costuma bastar: 1,5–9 GB) ou use Redis Cluster.
- ~20k `SET NX`/s no pico está bem abaixo da capacidade de uma única instância (>100k ops/s).

## Teste de validação

```bash
k6 run loadtest/ingest.js   # thresholds: p95 < 200ms, erro < 0,1%
```

O cenário roda 5 minutos a 5k ev/s e depois rampa e sustenta 20k ev/s por 3 minutos.
Acompanhe no Grafana: latência p95, lag do grupo `event-persistence` e duração dos lotes.
