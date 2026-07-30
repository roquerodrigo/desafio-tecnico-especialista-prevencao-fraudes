<!--
Sync Impact Report
==================
Version change: TEMPLATE (unversioned) → 1.0.0
Rationale: primeira ratificacao. Todos os placeholders substituidos por governanca
concreta da plataforma de analise de risco.

Principles defined (7):
  I.   Clean Architecture Rasa
  II.  Ubiquitous Language em Portugues
  III. Configuracao e Dado, Nao Codigo
  IV.  Estado Invalido Inexpressavel
  V.   Testabilidade Obrigatoria (NAO NEGOCIAVEL)
  VI.  Degradacao Explicita
  VII. Observabilidade com Privacidade

Added sections:
  - Restricoes Tecnicas (stack, topologia, nomenclatura, analise assintotica)
  - Fluxo de Desenvolvimento e Portoes de Qualidade
  - Governance

Removed sections: none (initial ratification)

Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check preenchido pelo /speckit-plan
  ✅ .specify/templates/spec-template.md — revisado, sem mudanca exigida pela constituicao
  ✅ .specify/templates/tasks-template.md — revisado, categorias cobrem principios V e VII

Follow-up TODOs: none
-->

# Constituicao da Plataforma de Analise de Risco (Banco ACME)

## Core Principles

### I. Clean Architecture Rasa

O dominio MUST ser Java puro: sem anotacao de framework, sem dependencia de Spring,
Jackson, JPA ou driver de banco. Dependencias apontam sempre para dentro — a
infraestrutura conhece o dominio, nunca o inverso.

Cada servico organiza-se em tres camadas e nada mais:

- `dominio` — entidades, regras, invariantes. Zero dependencia externa.
- `aplicacao` — casos de uso que orquestram o dominio, expressos em portas (interfaces).
- `infraestrutura` — adaptadores: controllers REST, repositorios, clients HTTP, listeners.

"Rasa" MUST ser lido literalmente: **e proibido criar camada adicional** (nem `servico`,
nem `facade`, nem `manager`) sem justificativa registrada em ADR. O objetivo e Clean
Architecture legivel em uma sentada, nao uma hierarquia cerimonial.

**Rationale**: o dominio (motor de regras, composicao por chave, calculo de score) e a
parte que se testa isoladamente e a primeira que o avaliador le. Framework no dominio
torna esse teste caro e a leitura confusa.

### II. Ubiquitous Language em Portugues

O negocio e prevencao a fraudes em um banco brasileiro; o codigo MUST falar a lingua do
negocio.

- Classes, metodos, campos, rotas e contratos JSON: portugues.
- Sufixo de pattern permanece em ingles: `RegraRepository`, `CondicaoStrategy`,
  `RegraFactory`. Patterns tem nomes proprios reconheciveis.
- Termos tecnicos consolidados permanecem: `score`, `id`, `ip`, `cpf`, `PIX`.
- **Identificadores MUST NOT conter acento** — `decisao`, `situacao`, `MEDIO`. Java aceita
  acentos, mas isso quebra em encoding e teclado.
- Acentuacao correta MUST aparecer onde e lida por humano: mensagens de erro, descricoes
  OpenAPI, README, ADRs.

Case por camada, sem excecao:

| Camada | Case |
|---|---|
| JSON e Java (campos/metodos) | `camelCase` |
| Classes | `PascalCase` |
| Enums e constantes | `SCREAMING_SNAKE_CASE` |
| Colunas Postgres | `snake_case` |
| Atributos DynamoDB | `camelCase` |
| Nomes de tabela | `snake_case` |
| Rotas | `kebab-case`, plural |
| Topicos Kafka | `entidade.evento` |

**Rationale**: esta e a definicao de Ubiquitous Language do DDD. Um glossario que traduz
"transaction" para "transacao" a cada fronteira e atrito puro.

### III. Configuracao e Dado, Nao Codigo

Nenhuma regra de negocio parametrizavel MUST existir em codigo-fonte.

- **Zero regra hardcoded.** Nao ha conjunto default compilado. O conjunto padrao e o
  registro com `tipoTransacao = 'PADRAO'`, cadastrado pelo mesmo CRUD que os demais.
- Faixas de score, incluindo a decisao (`APROVADA`/`NEGADA`) de cada faixa, MUST ser dado
  persistido e alteravel por endpoint administrativo.
- Alteracao de regra ou faixa MUST surtir efeito **sem redeploy e sem restart**.
- Massa inicial entra por migration (regras) ou pelo endpoint de carga (listas) — nunca por
  constante em classe.

**Rationale**: o requisito explicito e que usuarios de negocio gerenciem regras. Toda
regra em codigo e uma regra que exige engenheiro e janela de deploy.

### IV. Estado Invalido Inexpressavel

Tornar o estado invalido impossivel de representar MUST ser preferido a valida-lo depois.

- Faixas (de valor e de score) MUST ser modeladas **apenas por limite superior**. O limite
  inferior e o teto da faixa anterior. Gap e sobreposicao deixam de ser expressaveis,
  qualquer que seja a composicao de regras.
- `limiteSuperior` nulo significa "sem teto" — nunca um sentinela numerico.
- Invariantes estruturais MUST ser verificadas **na carga do cache**, nao no caminho da
  transacao: cada escada precisa de exatamente uma faixa sem teto e limites superiores
  distintos.
- Violacao de invariante MUST falhar alto, registrar erro e **preservar o cache anterior**.
  Servir cache vazio ou incompleto e proibido.

**Rationale**: em antifraude, configuracao silenciosamente quebrada produz falso negativo —
transacao fraudulenta aprovada. Falhar na carga e barato; falhar na transacao, nao.

### V. Testabilidade Obrigatoria (NAO NEGOCIAVEL)

- JaCoCo com **gate global de 90%**. Abaixo disso o build MUST falhar. O gate MUST NOT ser
  reduzido nem contornado para viabilizar entrega.
- O dominio MUST ser testavel sem Spring, sem banco e sem rede — teste unitario puro.
- Todo servico MUST ter teste de integracao com Testcontainers (Postgres, DynamoDB, Kafka)
  e WireMock para os vizinhos HTTP.
- Cada regra de negocio do enunciado MUST ter teste nomeado que a exercite: composicao por
  chave, piso do score, operador `OU` disparando uma unica vez, degradacao, fail-closed.

**Rationale**: cobertura e criterio explicito de avaliacao, e o gate no build e a unica
prova de que a disciplina nao depende de boa vontade.

### VI. Degradacao Explicita

Cada dependencia externa MUST declarar seu modo de falha em codigo e em documentacao.
Falha silenciosa e `catch` vazio sao proibidos.

- **Servico de Listas indisponivel** → degrada: prossegue como "nao consta em nenhuma
  lista". A transacao ainda e avaliada pelas regras de valor. Sinal de lista e desejavel,
  nao essencial.
- **Motor de Decisao indisponivel** → fail-closed: sem score nao existe decisao. Responde
  `503` com `Retry-After`, nunca `200` com `NEGADA`. `NEGADA` MUST significar decisao real
  de risco.
- Todo client HTTP MUST declarar timeout de conexao e de leitura explicitos.
- Toda fronteira MUST traduzir o modelo do vizinho para o proprio (ACL) — modelo de outro
  servico nao entra no dominio.

**Rationale**: distinguir sinal opcional de sinal essencial e decisao de risco, nao de
infraestrutura, e MUST ser visivel no codigo.

### VII. Observabilidade com Privacidade

- Todos os servicos MUST emitir log estruturado (JSON) com `X-Correlation-Id` propagado via
  MDC.
- CPF, IP e identificador de dispositivo MUST ser mascarados em log. Logs trafegam para
  agregacao e retencao ampla.
- A trilha de decisao MUST persistir CPF em claro: e base de investigacao de fraude e de
  defesa em contestacao. Acesso a trilha e controlado; log e trilha tem publicos
  diferentes.
- A resposta ao cliente MUST expor apenas a decisao. Score e classificacao MUST NOT
  aparecer em corpo, header ou mensagem de erro.
- A trilha MUST ser gravada fora do caminho critico da resposta.

**Rationale**: o requisito e simultaneamente auditar e nao vazar. Separar o dado de
operacao (log, mascarado) do dado de negocio (trilha, em claro) atende os dois.

## Restricoes Tecnicas

**Stack** — desvio exige ADR:

- Java 25 (LTS), Spring Boot 4.x
- Gradle com **Groovy DSL** (`build.gradle`), wrapper versionado. **Kotlin e proibido em
  qualquer camada, incluindo build scripts.**
- Postgres com Flyway; DynamoDB (`amazon/dynamodb-local` em desenvolvimento) com AWS SDK v2
  e `DynamoDbEnhancedClient`, sem `spring-cloud-aws`
- Kafka em modo KRaft
- JUnit 5, Mockito, Testcontainers, WireMock, JaCoCo, springdoc-openapi

**Topologia** — monorepo multi-modulo, 4 deployables:

| Modulo | Responsabilidade |
|---|---|
| `api-analise-risco` | Orquestra, classifica score, decide |
| `servico-listas` | Consulta CPF/IP/dispositivo |
| `motor-decisao` | CRUD de regras e calculo de score |
| `servico-auditoria` | Persiste a trilha de decisoes |

- **Database-per-service.** Um schema por servico; nenhum servico le a tabela de outro.
- **Modulo compartilhado e proibido.** DTOs de fronteira sao duplicados de proposito e
  traduzidos na ACL. Biblioteca comum acoplaria o ciclo de release dos quatro.
- A estrategia de acesso a dado MUST seguir o perfil do dado: volume alto com lookup
  pontual vai a banco sem cache local; volume baixo lido em toda transacao vai a cache em
  memoria invalidado por evento.
- Invalidacao de cache MUST ser por evento Kafka. TTL, polling e refresh agendado sao
  proibidos como mecanismo de atualizacao.

**Analise assintotica** — toda operacao no caminho critico MUST ter complexidade declarada
no README. Escolha de estrutura de dados MUST ser justificada pela complexidade que
entrega.

## Fluxo de Desenvolvimento e Portoes de Qualidade

1. Build verde e gate de cobertura de 90% sao pre-requisito de conclusao. "Funciona na
   minha maquina" nao encerra tarefa.
2. Toda decisao arquitetural MUST ser registrada em ADR proprio, com alternativas
   consideradas e o motivo da rejeicao.
3. Toda premissa assumida diante de ambiguidade do enunciado MUST constar no README, com a
   motivacao.
4. Comentario em codigo MUST ser excecao, reservado a "porque" nao obvio ou gotcha. Nome
   claro substitui comentario.
5. `docker compose up` MUST entregar o sistema executavel e testavel.

## Governance

Esta constituicao MUST prevalecer sobre preferencia pessoal, convencao herdada e habito de
ecossistema. Codigo existente que a contradiga MUST ser ajustado — a regra vence, nao o
codigo.

**Emendas** exigem: (a) registro do que muda e por que, (b) incremento de versao conforme a
politica abaixo, (c) propagacao aos templates e artefatos dependentes na mesma alteracao.

**Politica de versionamento**:

- MAJOR — remocao ou redefinicao incompativel de principio
- MINOR — novo principio ou expansao material de orientacao
- PATCH — esclarecimento, redacao, correcao sem efeito semantico

**Conformidade**: todo plano de implementacao MUST passar pelo Constitution Check antes da
fase de pesquisa e ser reavaliado apos o design. Complexidade nao justificada MUST ser
removida ou registrada em Complexity Tracking, com a alternativa mais simples que foi
rejeitada e o motivo.

**Version**: 1.0.0 | **Ratified**: 2026-07-29 | **Last Amended**: 2026-07-29
