# ADR-0002 — Idempotência em duas camadas (Redis + chave natural no banco)

**Status**: aceito

## Contexto
Clientes reenviam em timeouts; o Kafka entrega pelo-menos-uma-vez. Precisamos de efeito exatamente-uma-vez
no estado persistido sem custo de latência relevante.

## Decisão
1. `Idempotency-Key` (header ou campo do item em lote) → id determinístico `UUID(tenant:chave)`.
2. **Redis `SET NX` com TTL** responde rapidamente a reenvios (200 + `DUPLICATE`). Falha aberto.
   Só é consultado quando há chave — sem chave, o id é aleatório e não há reenvio possível de detectar.
3. **PostgreSQL**: PK `(id, occurred_at)` + `ON CONFLICT DO NOTHING` é a garantia final.
4. Se o broker não confirmar a escrita, a chave é liberada no Redis para o reenvio ser aceito.

## Consequências
- (+) Redis fora do ar não afeta disponibilidade nem correção.
- (+) Reentregas do Kafka são inofensivas.
- (−) A PK inclui `occurred_at` (exigência do particionamento); o mesmo id com `occurred_at` diferente
  seria aceito como outro evento. Aceitável: o `occurred_at` é definido pelo cliente e estável em reenvios.
