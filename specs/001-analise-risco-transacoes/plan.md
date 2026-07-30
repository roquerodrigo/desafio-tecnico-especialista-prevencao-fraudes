# Implementation Plan: Plataforma de Analise de Risco de Transacoes

**Branch**: `001-analise-risco-transacoes` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-analise-risco-transacoes/spec.md`

## Summary

Plataforma distribuida que avalia o risco de transacoes financeiras e devolve uma decisao de
aprovacao. Quatro servicos HTTP em monorepo: orquestracao da analise, consulta de listas
restritivas e permissivas, motor de regras dinamicas e trilha de auditoria.

O nucleo tecnico e o motor de decisao. Ele compoe dois conjuntos de regras — o padrao e o do
tipo de transacao — por uma chave de sobreposicao, e avalia duas naturezas distintas de regra:
escadas de faixa mutuamente exclusivas, resolvidas em O(log n) por `NavigableMap`, e regras
condicionais cumulativas, avaliadas por Strategy despachada em O(1) e combinada por Composite.
Nenhuma regra existe em codigo: o conjunto inteiro, inclusive o padrao, e dado gerenciavel por
CRUD, com cache em memoria invalidado por evento Kafka.

## Technical Context

**Language/Version**: Java 25.0.1 (LTS)

**Primary Dependencies**: Spring Boot 4.1.0, spring-kafka 4.1.0, springdoc-openapi 3.0.3,
AWS SDK v2 `dynamodb-enhanced` 2.49.5, Flyway (versao gerenciada pelo BOM)

**Storage**: PostgreSQL com schema por servico (regras, faixas de score, trilha de decisao);
DynamoDB com tres tabelas (listas de CPF, IP e dispositivo)

**Testing**: JUnit 6.1.2, Mockito, Testcontainers 2.0.5 (`testcontainers-postgresql`,
`testcontainers-kafka`, `testcontainers-junit-jupiter`), WireMock 3.13.2 (`wiremock-jetty12`),
JaCoCo 0.8.15 com gate global de 90%

**Target Platform**: JVM em conteiner Linux; orquestracao local por Docker Compose

**Project Type**: Monorepo Gradle multi-modulo — quatro servicos web independentes

**Performance Goals**: 150 ms no percentil 95 para a analise completa; propagacao de mudanca de
configuracao em ate 5 s; consulta de listas em tempo constante sobre ordem de milhoes de
entradas

**Constraints**: resposta ao cliente expoe apenas a decisao; alteracao de regra ou faixa sem
redeploy e sem restart; dados sensiveis mascarados em log; aritmetica monetaria decimal exata

**Scale/Scope**: 4 servicos, 3 topicos Kafka, 4 schemas, ordem de milhoes de entradas de lista,
dezenas de regras por tipo de transacao

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado apos a Phase 1.*

| Principio | Portao | Pre-design | Pos-design |
|---|---|---|---|
| I. Clean Architecture Rasa | Dominio sem anotacao de framework; exatamente tres camadas | PASS | PASS |
| II. Ubiquitous Language pt-BR | Nomes de dominio em portugues, sem acento em identificador; case por camada | PASS | PASS |
| III. Configuracao e Dado | Zero regra em codigo; alteracao sem redeploy | PASS | PASS |
| IV. Estado Invalido Inexpressavel | Faixas so por teto; invariantes na carga | PASS | PASS |
| V. Testabilidade (NAO NEGOCIAVEL) | Gate de 90%; dominio testavel sem infra | PASS | PASS |
| VI. Degradacao Explicita | Modo de falha declarado por dependencia; timeouts explicitos | PASS | PASS |
| VII. Observabilidade com Privacidade | Log estruturado mascarado; trilha fora do caminho critico | PASS | PASS |

**Notas de conformidade**:

- Principio I: o pacote `dominio` nao recebe dependencia alguma no `build.gradle` alem da
  linguagem. Isso e verificavel por inspecao do arquivo de build, nao apenas por convencao.
- Principio III: o conjunto `PADRAO` e uma linha de tabela semeada por migration, nao uma
  constante. Migration semeia dado; nao e codigo de regra.
- Principio V: exclusoes de cobertura limitam-se a classes de bootstrap do Spring e records de
  DTO sem logica, cada uma justificada no `build.gradle`.
- Principio VI: `servico-auditoria` consome evento e nao participa do caminho critico; sua
  indisponibilidade nao afeta a decisao (FR-035).

## Project Structure

### Documentation (this feature)

```text
specs/001-analise-risco-transacoes/
├── plan.md              # Este arquivo
├── spec.md              # Especificacao funcional
├── research.md          # Phase 0 — matriz de versoes e decisoes tecnicas
├── data-model.md        # Phase 1 — entidades, schemas, invariantes
├── quickstart.md        # Phase 1 — como subir e validar
├── contracts/           # Phase 1 — contratos REST e de evento
│   ├── api-analise-risco.md
│   ├── servico-listas.md
│   ├── motor-decisao.md
│   └── eventos-kafka.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — gerado por /speckit-tasks
```

### Source Code (repository root)

```text
settings.gradle                     # inclui os 4 modulos
build.gradle                        # convencoes comuns: toolchain, JaCoCo, deps de teste
gradle/wrapper/                     # wrapper versionado
docker-compose.yml                  # postgres, dynamodb-local, kafka, 4 apps, seed
docs/
├── adr/                            # um ADR por decisao arquitetural
└── decisoes-autonomas.md           # decisoes tomadas em execucao autonoma

api-analise-risco/
├── build.gradle
└── src/
    ├── main/java/br/com/acme/analiserisco/
    │   ├── dominio/                # Transacao, FaixaScore, Decisao, ClassificacaoRisco
    │   ├── aplicacao/              # AnalisarRiscoUseCase + portas
    │   └── infraestrutura/
    │       ├── web/                # controllers, DTOs, erro RFC 9457
    │       ├── listas/             # client HTTP + ACL (degrada)
    │       ├── motor/              # client HTTP + ACL (fail-closed)
    │       ├── persistencia/       # faixas de score
    │       ├── mensageria/         # publica decisoes, consome faixas.atualizadas
    │       └── observabilidade/    # correlacao, mascaramento
    ├── main/resources/db/migration/
    └── test/java/...

servico-listas/
└── src/main/java/br/com/acme/listas/
    ├── dominio/                    # Variavel, Pertinencia, ResultadoConsulta
    ├── aplicacao/
    └── infraestrutura/{web,dynamodb,observabilidade}

motor-decisao/
└── src/main/java/br/com/acme/motordecisao/
    ├── dominio/
    │   ├── regra/                  # Regra, Escada, Condicao, Acao, Campo, Operador
    │   ├── avaliacao/              # AvaliadorCondicao (Strategy), Composite, Registry
    │   └── composicao/             # merge por chave, ConjuntoEfetivo
    ├── aplicacao/                  # CalcularScoreUseCase, CRUD de regras
    └── infraestrutura/{web,persistencia,cache,mensageria,observabilidade}

servico-auditoria/
└── src/main/java/br/com/acme/auditoria/
    ├── dominio/                    # TrilhaDecisao
    ├── aplicacao/
    └── infraestrutura/{web,persistencia,mensageria}
```

**Structure Decision**: monorepo Gradle multi-modulo com quatro modulos independentes, cada um
um Spring Boot executavel. Nao existe modulo compartilhado: DTOs de fronteira sao duplicados de
proposito e traduzidos na camada de ACL. Biblioteca comum acoplaria o ciclo de release dos
quatro servicos, o que contraria a evolucao independente esperada em MSA — duplicar tres
records e mais barato que esse acoplamento.

Dentro de cada modulo, a divisao e `dominio` / `aplicacao` / `infraestrutura`, com dependencias
apontando para dentro.

## Fluxo da analise

```text
Cliente
  │  POST /v1/analises-risco  { cpf, ip, idDispositivo, tipoTransacao, valorTransacao }
  ▼
api-analise-risco
  │  valida entrada (CPF com DV, valor > 0 com 2 casas decimais)
  ├─────────────► servico-listas   POST /v1/consultas-listas
  │               (timeout; falha ⇒ degrada para "nao consta")
  │◄───────────── { cpf:{...}, ip:{...}, dispositivo:{...} }
  │
  ├─────────────► motor-decisao    POST /v1/scores
  │               (timeout; falha ⇒ fail-closed ⇒ 503 + Retry-After)
  │◄───────────── { score }
  │
  │  classifica score na faixa (ceilingEntry, O(log f)) ⇒ decisao da faixa
  ├─────────────► Kafka  decisoes.registradas  (assincrono, fora do caminho critico)
  ▼
Cliente  200 { decisao: "APROVADA" }        ← nunca contem score nem classificacao
                                                   │
                                     servico-auditoria consome e persiste a trilha
```

## Complexity Tracking

> Preenchido porque a topologia excede o minimo que o problema exigiria.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 4 deployables em vez de 1 monolito modular | O enunciado pede sistema distribuido e repete tres vezes a formula "desenvolver um servico HTTP REST"; a separacao permite demonstrar ACL, timeout por dependencia e modos de falha distintos por vizinho | Monolito modular atenderia o dominio com menos infraestrutura, mas o avaliador poderia ler como nao atendimento ao requisito explicito de arquitetura distribuida |
| `servico-auditoria` como 4o deployable | Isola a trilha do caminho critico, permite escala independente e a trilha sobrevive a queda da API | Um `@KafkaListener` no proprio deployable da API daria o mesmo desacoplamento temporal com um servico menos. Decisao do stakeholder pela forma ortodoxa; registrada como a candidata natural a corte se o escopo precisar encolher |
| Kafka para invalidacao de cache | Um `POST` administrativo atinge uma unica replica; sem fan-out por evento as demais servem configuracao velha, e duas replicas passam a decidir diferente sobre a mesma transacao | TTL curto foi rejeitado por criar janela de inconsistencia entre replicas; leitura direta do banco a cada transacao foi rejeitada por colocar I/O no caminho critico, contra o RNF de latencia |
| DynamoDB alem do Postgres | Listas tem perfil de dado oposto ao das regras: milhoes de entradas com lookup pontual, contra dezenas lidas integralmente a cada transacao. Justifica store e estrategia de acesso proprios | Postgres com indice B-tree resolveria em O(log n) e removeria um componente. Decisao do stakeholder por DynamoDB, apos descartar Redis pela janela de perda do AOF |
| Tres tabelas DynamoDB em vez de single-table | Schemas genuinamente diferentes (CPF tem duas pertinencias, IP e dispositivo uma), TTL so faz sentido em IP, e `BatchGetItem` aceita multiplas tabelas — o round-trip unico e preservado | Single-table design existe para colocar entidades **relacionadas** na mesma particao e evitar join. Aqui sao tres dominios de lookup independentes: aplicar o padrao seria seguir a forma sem o problema que ela resolve |

## Phase 1 — Artefatos gerados

- [data-model.md](./data-model.md) — entidades de dominio, schemas Postgres, tabelas DynamoDB,
  invariantes e complexidade por operacao
- [contracts/api-analise-risco.md](./contracts/api-analise-risco.md) — analise de risco e
  administracao de faixas
- [contracts/servico-listas.md](./contracts/servico-listas.md) — consulta e carga de listas
- [contracts/motor-decisao.md](./contracts/motor-decisao.md) — score e CRUD de regras
- [contracts/eventos-kafka.md](./contracts/eventos-kafka.md) — tres topicos e seus payloads
- [quickstart.md](./quickstart.md) — como subir, semear e validar ponta a ponta
