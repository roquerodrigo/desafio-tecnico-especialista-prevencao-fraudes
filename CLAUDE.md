# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Plataforma de análise de risco de transações: monorepo Gradle com cinco Spring Boot executáveis sob
`modulos/`. Desenvolvido com [Spec Kit](https://github.com/github/spec-kit) — **o artefato vem antes
do código**.

## Fluxo de desenvolvimento (Spec Kit)

Nenhuma mudança de comportamento começa pelo código. As skills ficam em `.claude/skills/` e são
invocáveis por `/speckit-<nome>`:

| Skill | Produz | Quando |
|---|---|---|
| `/speckit-constitution` | `.specify/memory/constitution.md` | única via para alterar os princípios |
| `/speckit-specify` | `spec.md` | comportamento novo, em linguagem de negócio |
| `/speckit-clarify` | seção *Clarifications* na spec | antes do plano, para ambiguidade residual |
| `/speckit-plan` | `plan.md`, `research.md`, `data-model.md`, `contracts/` | design e Constitution Check |
| `/speckit-tasks` | `tasks.md` | tarefas ordenadas por dependência |
| `/speckit-analyze` | correções nos artefatos | consistência cruzada spec × plano × tarefas |
| `/speckit-implement` | código | execução das tarefas |
| `/speckit-converge` | novas tarefas em `tasks.md` | reconcilia o código entregue com o que a spec pedia |

Complementares: `/speckit-checklist` valida a **qualidade dos requisitos escritos** (não testa
implementação) e `/speckit-taskstoissues` converte tarefas em issues do GitHub.

Os artefatos da feature corrente vivem em `specs/001-analise-risco-transacoes/`; o ponteiro é
`.specify/feature.json`. Templates em `.specify/templates/`, scripts de apoio em
`.specify/scripts/python/`.

Regras do fluxo:

- **Todo plano passa pelo Constitution Check** antes da pesquisa e é reavaliado após o design.
  Complexidade não justificada é removida ou registrada em Complexity Tracking, com a alternativa
  mais simples que foi rejeitada.
- **Marcar `[x]` em `tasks.md` só depois de verificar o arquivo ou teste correspondente.** Marcação
  em massa por script é proibida — ela afirma conclusão sem verificá-la.
- Emenda à constitution exige registro do que muda, bump de versão (MAJOR/MINOR/PATCH conforme o
  próprio documento) e propagação aos templates na mesma alteração.
- Decisão arquitetural nova → ADR em `docs/adr/`, com alternativas e motivo da rejeição.
- Premissa nova diante de ambiguidade do enunciado (`docs/desafio.md`) → seção "Premissas assumidas"
  do README, com a motivação.

## Comandos

```bash
docker compose up -d --build          # sobe tudo: postgres, dynamodb-local, kafka, 5 apps, seed
docker compose down -v                # derruba e zera os dados

./gradlew clean build                 # testes + gate de cobertura de 90% (portão de conclusão)
./gradlew test --tests '*Test'        # apenas unitários, sem Docker
./gradlew test --tests '*IT'          # apenas integração (exige Docker)
./gradlew :motor-decisao:test --tests '*AvaliadorRegraTest'      # um teste de um módulo
```

`*Test.java` = unitário puro; `*IT.java` = integração com Testcontainers e WireMock, sob
`@ActiveProfiles("teste")`. Não há linter separado: o portão é o `build`, que encadeia
`jacocoTestCoverageVerification`.

Portas: 8080 api-analise-risco · 8081 servico-listas · 8082 motor-decisao · 8083 servico-auditoria ·
8084 gerador-trafego (auxiliar, sobe ocioso). Chave administrativa de desenvolvimento:
`chave-desenvolvimento`.

## Arquitetura

Quatro serviços de negócio mais um auxiliar. `api-analise-risco` orquestra: consulta
`servico-listas` (**degrada** — falha vira "não consta em lista"), chama `motor-decisao`
(**fail-closed** — sem score responde `503`, nunca `200 NEGADA`), classifica o score em faixa,
decide e publica em Kafka; `servico-auditoria` consome e persiste a trilha.

Cada módulo tem três camadas e nada mais — `dominio` (Java puro, zero framework), `aplicacao`
(casos de uso e portas), `infraestrutura` (adaptadores) — com dependências apontando para dentro.

O que exige ler vários arquivos para entender:

- **A estratégia de persistência segue o perfil do dado.** Listas (milhões de entradas, lookup
  pontual) vão ao DynamoDB **sem cache local**; regras e faixas (dezenas de registros, lidos em toda
  transação) ficam em cache de memória invalidado por evento Kafka. TTL, polling e refresh agendado
  são proibidos como mecanismo de atualização.
- **Sem módulo compartilhado.** DTOs de fronteira são duplicados de propósito e traduzidos na ACL de
  cada consumidor. Não criar biblioteca comum (ADR 0008).
- **`settings.gradle` redireciona `projectDir`**: os módulos moram em `modulos/`, mas o caminho
  Gradle é liso (`:motor-decisao`, não `:modulos:motor-decisao`).
- **Um `infra/Dockerfile` parametrizado por `MODULO`.** Ele copia os cinco `build.gradle` porque o
  Gradle exige que todo diretório declarado em `settings.gradle` exista, mesmo para construir um só.
- Versões centralizadas em `gradle.properties`; convenções comuns e as exclusões do gate em
  `build.gradle` na raiz.

## Invariantes da constitution (não negociáveis)

Leia `.specify/memory/constitution.md` antes de decidir qualquer coisa estrutural. Os pontos que mais
afetam código novo:

- **Domínio sem framework.** Nenhuma anotação de Spring, Jackson ou JPA em `dominio`.
- **Nenhuma camada além das três.** Nada de `servico`, `facade` ou `manager` sem ADR.
- **Zero regra de negócio em código.** Regras, faixas e a decisão de cada faixa são dado persistido,
  alterável sem redeploy. Conjunto padrão é o registro `tipoTransacao = 'PADRAO'`, não código.
- **Faixas modeladas apenas por limite superior**; `limiteSuperior` nulo = sem teto, nunca sentinela.
  Invariantes verificadas na carga do cache, não no caminho da transação — e falha na carga preserva
  o cache anterior.
- **Gate de 90% no JaCoCo**, sem redução e sem exclusão de módulo para viabilizar entrega.
- **Log mascara CPF/IP/dispositivo; a trilha persiste CPF em claro; a resposta ao cliente expõe só a
  decisão** — score e classificação não aparecem em corpo, header ou mensagem de erro.
- **Todo client HTTP declara timeout de conexão e leitura**, e toda fronteira traduz o modelo do
  vizinho para o próprio.

## Convenções de nomenclatura

Ubiquitous Language em **português** (ADR 0007) — isto tem precedência sobre a regra global de
escrever código em inglês. Commits, PRs e changelogs continuam em inglês.

- Identificadores **sem acento** (`decisao`, `situacao`, `MEDIO`); acentuação correta só onde humano
  lê: mensagens de erro, OpenAPI, README, ADRs.
- Sufixo de pattern em inglês: `RegraRepository`, `CondicaoStrategy`, `RegraFactory`.
- Case por camada: `camelCase` em JSON e Java, `PascalCase` em classes, `SCREAMING_SNAKE_CASE` em
  enums, `snake_case` em colunas e tabelas Postgres, `camelCase` em atributos DynamoDB,
  `kebab-case` plural em rotas, `entidade.evento` em tópicos Kafka.
- Comentário é exceção, reservado a "porquê" não óbvio ou gotcha.

## Gotchas verificados

- **`BigDecimal.equals` considera escala** — `300` não casa com `300.00`. Comparação de valor sempre
  por `compareTo`.
- **`Boolean` implementa `Comparable`** — `true.compareTo(false)` devolve `1`, o que faria
  `CPF_EM_LISTA_PERMISSIVA MAIOR_QUE 5` acionar para todo CPF em lista. A comparação ordinal sobre
  booleano é bloqueada em `RegistroAvaliadores`.
- **Kotlin é proibido em qualquer camada**, inclusive nos build scripts (Groovy DSL).
- **Spring Boot 4 modularizou as autoconfigurações de teste**: `MockMvc` e `@ServiceConnection` vêm
  de `spring-boot-webmvc-test` e `spring-boot-testcontainers`, não do `starter-test`.
- **Testcontainers 2.x renomeou os artefatos** (`testcontainers-postgresql`, `testcontainers-kafka`);
  JaCoCo abaixo de 0.8.14 não lê bytecode de Java 25.
