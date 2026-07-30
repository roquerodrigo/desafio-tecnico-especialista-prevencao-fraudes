# ADR 0001 — Topologia de quatro serviços

**Status**: aceito · **Data**: 2026-07-29

## Contexto

O enunciado pede um "sistema distribuído baseado em HTTP REST" com "aderência a boas práticas de
microsserviços", e repete três vezes a fórmula "desenvolver um serviço HTTP REST" — para
orquestração, listas e motor de decisão. Ao mesmo tempo, avisa que o diagrama apresentado
"não reflete a arquitetura técnica" e que a divisão de serviços é decisão do candidato.

## Decisão

Quatro deployables independentes em monorepo Gradle: `api-analise-risco`, `servico-listas`,
`motor-decisao` e `servico-auditoria`.

> **Nota (2026-07-30)**: o repositório contém um quinto módulo, `gerador-trafego`, adicionado
> depois. Ele é **auxiliar** — não participa do fluxo de análise e sua ausência não afeta nenhum
> requisito funcional —, por isso está fora da contagem deste ADR, que trata da topologia de
> negócio. O racional dele está no [ADR 0009](0009-gerador-trafego-como-modulo-java.md).

## Consequências

**Positivas**

- Cada serviço declara um modo de falha próprio, e a diferença entre eles é visível no código:
  listas degradam, motor é fail-closed. Num monolito essa distinção seria apenas convenção.
- A camada de ACL existe de fato: cada fronteira traduz o modelo do vizinho, com timeout explícito.
- `motor-decisao` e `servico-listas` escalam independentemente, o que importa porque têm perfis de
  carga distintos — o motor é CPU sobre dado pequeno em memória, listas é I/O sobre dado grande.

**Negativas**

- Quatro processos para subir, observar e testar.
- DTOs de fronteira duplicados (ver [ADR 0008](0008-sem-modulo-compartilhado.md)).
- O `servico-auditoria` é o componente cuja justificativa é mais arquitetural que funcional — um
  `@KafkaListener` no próprio deployable da API entregaria o mesmo desacoplamento temporal. É o
  candidato natural a corte se o escopo precisar encolher.

## Alternativas consideradas

**Monolito modular** — um deployable com três bounded contexts isolados por pacote. Atenderia o
domínio com muito menos infraestrutura e seria defensável como "modular monolith first". Rejeitado
porque o requisito de arquitetura distribuída é explícito, e um avaliador poderia ler o monolito
como não atendimento.

**Dois serviços** — motor separado, listas embutido na orquestração. Rejeitado por ser o
meio-termo mais difícil de justificar: não simplifica de forma significativa e abandona metade do
argumento de MSA.
