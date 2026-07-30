# ADR 0008 — Sem módulo compartilhado entre os serviços

**Status**: aceito · **Data**: 2026-07-29

## Contexto

Os quatro serviços trocam mensagens com estruturas parecidas. O contrato de listas aparece na
`api-analise-risco` e no `servico-listas`; o evento de decisão aparece na `api-analise-risco` e no
`servico-auditoria`. Num monorepo, é tentador extrair um módulo `comum` com esses DTOs.

## Decisão

Não existe módulo compartilhado. Cada serviço define seus próprios DTOs de fronteira e traduz na
camada de ACL. Utilitários idênticos — `ValidadorCpf`, `MascaradorDadosSensiveis`, filtros — também
são duplicados.

## Racional

Um módulo compartilhado acopla o **ciclo de release** dos quatro serviços: mudar um contrato obriga
a recompilar e reimplantar todos em conjunto. Isso é exatamente o que a arquitetura de
microsserviços pretende evitar, e é um anti-pattern conhecido em MSA.

Duplicar três records é mais barato que esse acoplamento.

## Consequência que apareceu na prática

O consumo do evento de decisão **quebrou** na primeira execução. O `JsonSerializer` do Spring Kafka
grava um header `__TypeId__` com o nome da classe do produtor
(`br.com.acme.analiserisco...EventoDecisaoRegistrada`), que não existe no consumidor. O erro foi
`Class not found`.

A correção — `spring.json.use.type.headers: false` mais `spring.json.value.default.type` — transforma
o custo em ganho: desserializar no tipo local significa que o produtor pode renomear ou mover a
classe dele sem quebrar o consumidor. **O acoplamento fica no formato do JSON, não no nome da classe
Java** — que é onde ele deve estar entre serviços independentes.

## Consequências

- Evolução independente de fato: cada serviço versiona seu contrato.
- Duplicação real de código utilitário. `ValidadorCpf` existe em dois módulos e é testado nos dois.
- A tradução na ACL é explícita e testável — e é onde a política de degradação vive.
- Num projeto com muito mais serviços, valeria publicar os contratos como artefato versionado
  (schema registry ou biblioteca com versionamento semântico), não como módulo do mesmo build.
