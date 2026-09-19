# ADR-0004 — Contratos e versionamento

**Status**: aceito

## Decisão
- API REST versionada no caminho (`/api/v1`); mudanças incompatíveis → `/api/v2` convivendo com v1.
- Tópicos versionados no nome (`events.ingested.v1`); mensagem JSON com `schemaVersion`.
- O contrato da mensagem (`EventMessage`) é separado do modelo de domínio (`Event`).
- Regra de evolução: apenas adições opcionais dentro da mesma versão.

## Próximo passo
Adotar Schema Registry (Avro ou Protobuf) com compatibilidade `BACKWARD` validada no CI quando houver
consumidores de outros times — reduz o tamanho das mensagens (~40 %) e impede quebras acidentais.
