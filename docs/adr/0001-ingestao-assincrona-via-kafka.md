# ADR-0001 — Ingestão assíncrona com Kafka como log durável

**Status**: aceito

## Contexto
Requisitos: 5k ev/s sustentados, picos de 20k ev/s, p95 < 200 ms. Gravar diretamente no banco a cada
requisição acopla a latência da API à latência de escrita do PostgreSQL e faz com que picos derrubem o banco.

## Decisão
A API só confirma (202) após gravar o evento no Kafka com `acks=all`. A persistência é feita por
consumidores em lote. O tópico de processados permite fan-out para integrações e novos consumidores.

## Consequências
- (+) Latência da API independe do banco; picos são absorvidos como lag.
- (+) Reprocessamento: o log (7 dias) permite reconstruir projeções ou corrigir bugs.
- (+) Escala horizontal por partições.
- (−) Consistência eventual: um evento aceito leva de milissegundos a segundos para ser consultável
  (SLO de frescor p95 < 5 s).
- (−) Operar Kafka exige maturidade; mitigado por serviço gerenciado (MSK, Confluent Cloud, Aiven).

## Alternativas consideradas
- **Escrita síncrona no PostgreSQL**: simples, mas não sustenta 20k ev/s com p95 < 200 ms sem superdimensionar o banco.
- **RabbitMQ**: ótimo para filas de trabalho, mas sem log reprocessável nem particionamento ordenado nativo no mesmo nível.
- **Kinesis/Pub/Sub**: equivalentes válidos; a porta `EventLogPublisher` permite trocar.
