# Event Platform

Plataforma backend em **Java 21 / Spring Boot 3.5** para ingestão, processamento, persistência e
integração de eventos em alta vazão.

| Requisito | Como é atendido |
|---|---|
| 5.000 ev/s sustentados, picos de 20.000 ev/s | Kafka como buffer durável; consumidores em lote; escala horizontal (HPA 4→16 pods, 24 partições) |
| p95 < 200 ms | Banco fora do caminho crítico; threads virtuais; orçamento de 150 ms para o ack do broker; ZGC |
| Evolução sustentável | Arquitetura hexagonal verificada por ArchUnit; contratos versionados; ADRs |
| Qualidade | Testes unitários do núcleo sem infraestrutura, `@WebMvcTest`, Testcontainers, JaCoCo, k6 |
| Observabilidade | Prometheus + Grafana, OpenTelemetry, logs JSON com traceId, alertas por SLO |
| Segurança | OAuth2/JWT com escopos, isolamento por tenant via token, webhooks assinados, hardening de container |
| Eficiência operacional | Stateless, probes corretos, shutdown gracioso, DLT, runbook |

Leia primeiro: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** · [capacidade](docs/capacity-planning.md) ·
[runbook](docs/runbook.md) · [ADRs](docs/adr)

## Executando localmente

Pré-requisitos: JDK 21, Maven 3.9+, Docker.

```bash
docker compose up -d                                   # PostgreSQL, Kafka, Redis, Jaeger, Prometheus, Grafana
mvn spring-boot:run -Dspring-boot.run.profiles=local   # aplicação em :8080 (gerenciamento em :8081)
```

Ou tudo em containers: `docker compose --profile app up -d --build`.

### Testando a API

```bash
# 1. Token de desenvolvimento (só existe no perfil "local")
TOKEN=$(curl -s -X POST "localhost:8080/dev/token?tenant=acme" | jq -r .access_token)

# 2. Ingerir um evento
curl -i -X POST localhost:8080/api/v1/events \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -H "Idempotency-Key: pedido-123-criado" \
  -d '{"type":"order.created","source":"checkout","payload":{"orderId":"123","total":99.9}}'
# → 202 {"eventId":"…","outcome":"ACCEPTED"}   (reenviar a mesma chave → 200 DUPLICATE)

# 3. Consultar
curl -s localhost:8080/api/v1/events/<eventId> -H "Authorization: Bearer $TOKEN"
curl -s "localhost:8080/api/v1/events?type=order.created&limit=20" -H "Authorization: Bearer $TOKEN"
```

Lote (até 500 itens, 202 se todos aceitos, 207 se parcial): `POST /api/v1/events/batch` com
`{"events":[{...,"idempotencyKey":"..."}]}`.

| Ferramenta | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Métricas | http://localhost:8081/actuator/prometheus |
| Grafana (dashboard “Golden Signals”) | http://localhost:3000 |
| Jaeger (traces) | http://localhost:16686 |
| Prometheus | http://localhost:9090 |

## Testes

```bash
mvn verify                  # unitários, controller, arquitetura (ArchUnit) + cobertura JaCoCo
mvn verify -Pintegration    # + ponta a ponta com PostgreSQL/Kafka/Redis reais (Testcontainers)
k6 run loadtest/ingest.js   # carga: 5k ev/s por 5 min, depois pico de 20k ev/s; reprova se p95 ≥ 200 ms
```

## Configuração

Tudo por variáveis de ambiente (ver `src/main/resources/application.yml`). Principais:

| Variável | Padrão | Descrição |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` | localhost | PostgreSQL |
| `KAFKA_BOOTSTRAP`, `KAFKA_PARTITIONS`, `KAFKA_REPLICAS` | localhost:9092, 24, 3 | Kafka |
| `PERSISTENCE_CONCURRENCY`, `INTEGRATION_CONCURRENCY` | 4, 4 | consumidores por pod |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | localhost:6379 | idempotência e cache |
| `JWT_ISSUER_URI` | — | emissor OAuth2 (obrigatório no perfil `prod`) |
| `INTEGRATION_ENABLED`, `INTEGRATION_WEBHOOK_URL`, `INTEGRATION_WEBHOOK_SECRET` | false | integração externa |
| `OTLP_ENDPOINT`, `TRACING_SAMPLING` | localhost:4318, 0.05 | tracing |

## Deploy

```bash
docker build -t registry.example.com/event-platform:1.0.0 .
kubectl apply -k k8s/base
```

Os manifestos incluem Deployment com probes e hardening, HPA, PDB, NetworkPolicy e Ingress.
Segredos de exemplo em `k8s/base/config.yaml` devem ser substituídos por External Secrets/Vault.

## Estrutura

```
src/main/java/com/example/eventplatform
├── domain/        modelo e portas (Java puro)
├── application/   casos de uso (Java puro)
├── adapter/in/    REST, consumidores Kafka
├── adapter/out/   Kafka, PostgreSQL (JDBC), Redis, HTTP
├── config/        composição Spring, segurança, Kafka, cache
└── observability/ filtros e health checks
src/main/resources/db/migration   Flyway (tabela particionada por mês)
k8s/ · observability/ · loadtest/ · docs/
```
