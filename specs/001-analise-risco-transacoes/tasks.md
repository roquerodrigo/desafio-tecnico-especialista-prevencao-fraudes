---

description: "Task list — Plataforma de Analise de Risco de Transacoes"
---

# Tasks: Plataforma de Analise de Risco de Transacoes

**Input**: Design documents from `specs/001-analise-risco-transacoes/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: MANDATORY. Constitution Principle V (NAO NEGOCIAVEL) exige teste unitario de dominio,
teste de integracao por servico e gate JaCoCo global de 90% que quebra o build.

**Organization**: agrupadas por user story. Cada fase de story e um incremento entregavel e
testavel isoladamente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizavel (arquivos distintos, sem dependencia pendente)
- **[Story]**: user story a que pertence (US1..US7)

## Path Conventions

Monorepo Gradle multi-modulo com os modulos sob `modulos/`: `modulos/api-analise-risco/`, `modulos/servico-listas/`,
`modulos/motor-decisao/`, `modulos/servico-auditoria/`. Dentro de cada um:
`src/main/java/br/com/acme/<servico>/{dominio,aplicacao,infraestrutura}/`.

Convencao de teste: `*Test.java` = unitario (sem Docker); `*IT.java` = integracao (Testcontainers).

---

## Phase 1: Setup

**Purpose**: monorepo Gradle compilando com os quatro modulos vazios e o gate de cobertura ativo.

- [x] T001 Criar `settings.gradle` na raiz declarando os quatro modulos e `rootProject.name`
- [x] T002 Criar `build.gradle` raiz com convencoes comuns: toolchain Java 25, plugin Spring Boot 4.1.0, `io.spring.dependency-management`, repositorio Maven Central, e bloco `subprojects` aplicando java/jacoco
- [x] T003 Configurar JaCoCo 0.8.15 no `build.gradle` raiz com `jacocoTestCoverageVerification` em 90% de linha, task agregadora, e exclusoes justificadas por comentario (classes `*Application`, `*Configuration`, DTOs sem logica)
- [x] T004 Gerar o Gradle wrapper na versao 9.6.1 e versionar `gradlew`, `gradlew.bat` e `gradle/wrapper/`
- [x] T005 [P] Criar `modulos/api-analise-risco/build.gradle` com starters web/validation/actuator/data-jpa, spring-kafka, flyway, postgresql, springdoc 3.0.3 e deps de teste
- [x] T006 [P] Criar `modulos/servico-listas/build.gradle` com starters web/validation/actuator, `software.amazon.awssdk:dynamodb-enhanced:2.49.5`, springdoc e deps de teste
- [x] T007 [P] Criar `modulos/motor-decisao/build.gradle` com starters web/validation/actuator/data-jpa, spring-kafka, flyway, postgresql, springdoc e deps de teste
- [x] T008 [P] Criar `modulos/servico-auditoria/build.gradle` com starters web/actuator/data-jpa, spring-kafka, flyway, postgresql, springdoc e deps de teste
- [x] T009 Adicionar ao `build.gradle` raiz o `testcontainers-bom:2.0.5` e `wiremock-jetty12:3.13.2` nas dependencias de teste dos subprojetos, usando os artefatos com prefixo `testcontainers-`
- [x] T010 Criar as quatro classes `*Application` com `@SpringBootApplication` e validar `./gradlew build` verde com os modulos vazios

**Checkpoint**: `./gradlew build` compila os quatro modulos e o gate de cobertura esta ativo.

---

## Phase 2: Foundational (bloqueante)

**Purpose**: infraestrutura transversal exigida por todas as stories. Nenhuma story comeca antes.

- [x] T011 Criar `docker-compose.yml` com Postgres (**3** schemas: `analise_risco`, `motor_decisao`, `auditoria` — `servico-listas` nao usa Postgres), `amazon/dynamodb-local`, Kafka em KRaft, os quatro servicos e o container de seed, com `depends_on` por healthcheck
- [x] T012 [P] Criar `ValidadorCpf` em `modulos/api-analise-risco/src/main/java/br/com/acme/analiserisco/dominio/ValidadorCpf.java`, validando 11 digitos, digito verificador do modulo 11 e rejeitando sequencias de digito repetido
- [x] T013 [P] Criar `ValidadorCpfTest` cobrindo CPF valido, DV invalido, as onze sequencias repetidas, tamanho errado e caracteres nao numericos
- [x] T014 [P] Criar `MascaradorDadosSensiveis` em cada modulo que registra log, mascarando CPF (`529****4725`), IP (ultimo octeto) e UUID de dispositivo
- [x] T015 [P] Criar `MascaradorDadosSensiveisTest` verificando que nenhum valor completo sobrevive ao mascaramento
- [x] T016 Criar `FiltroCorrelacao` (servlet filter) nos quatro modulos: aceita `X-Correlation-Id`, gera se ausente, popula MDC e devolve no cabecalho da resposta
- [x] T017 [P] Configurar log estruturado JSON nos quatro `application.yml`, incluindo o campo de correlacao do MDC
- [x] T018 [P] Criar `TratadorErroGlobal` (`@RestControllerAdvice`) nos quatro modulos produzindo `ProblemDetail` conforme RFC 9457, com lista de erros de campo para `400`
- [x] T019 [P] Criar `FiltroApiKey` validando `X-Api-Key` apenas nas rotas administrativas, com a chave vinda de propriedade
- [x] T020 [P] Criar `FiltroApiKeyTest` verificando rota administrativa sem chave (`401`), com chave errada (`401`), com chave correta (`200`) e rota publica sem chave (`200`)
- [x] T021 Criar classe base de teste de integracao com Testcontainers reutilizavel (`@ServiceConnection` para Postgres e Kafka) em cada modulo que precise
- [x] T022 [P] Configurar springdoc nos quatro modulos com metadados de API e agrupamento de rotas administrativas

**Checkpoint**: validacao, mascaramento, correlacao, erro padronizado e autenticacao administrativa disponiveis. Compose sobe a infraestrutura.

---

## Phase 3: User Story 2 — Gerenciar regras sem novo deploy (P1)

**Story Goal**: CRUD de regras persistido, com efeito nas analises subsequentes sem restart.

**Independent Test**: cadastrar regra, consultar, alterar pontuacao, remover — verificando cada
efeito via API, sem reiniciar servico.

> Executada antes da US1 porque a US1 depende do motor para obter score. Sem regras nao ha score.

- [x] T023 [P] [US2] Criar enums `Campo`, `Operador`, `OperadorLogico`, `TipoAcao` em `modulos/motor-decisao/src/main/java/br/com/acme/motordecisao/dominio/regra/`
- [x] T024 [P] [US2] Criar value objects `Acao` e `Condicao` em `dominio/regra/`, com validacao na construcao (`pontos > 0`, campos obrigatorios)
- [x] T025 [US2] Criar o agregado `Regra` em `dominio/regra/Regra.java` com a invariante de natureza: escada nao coexiste com condicoes, e regra condicional exige condicoes nao vazias
- [x] T026 [P] [US2] Criar `RegraTest` cobrindo a invariante de natureza, rejeicao do hibrido e construcao valida de cada natureza
- [x] T027 [US2] Criar migration Flyway `V1__cria_schema_regras.sql` com tabelas `regra` e `condicao`, constraint `uk_regra_tipo_chave` e `ck_regra_natureza` conforme data-model.md
- [x] T028 [US2] Criar entidades JPA `RegraEntity` e `CondicaoEntity` em `infraestrutura/persistencia/`, mapeando colunas `snake_case`
- [x] T029 [US2] Criar `RegraRepository` (Spring Data) e `RegraMapper` traduzindo entidade↔dominio em `infraestrutura/persistencia/`
- [x] T030 [US2] Criar `GerenciarRegrasUseCase` em `aplicacao/` com criar, consultar, alterar e remover, publicando evento apos cada escrita
- [x] T031 [US2] Criar DTOs de requisicao e resposta de regra em `infraestrutura/web/dto/`, com Bean Validation conforme contracts/motor-decisao.md
- [x] T032 [US2] Criar `RegraController` em `infraestrutura/web/` expondo `POST/GET/PUT/DELETE /v1/regras`, com `201 + Location`, `404` e `409` para chave duplicada
- [x] T033 [P] [US2] Criar `GerenciarRegrasUseCaseTest` com repositorio dublado, cobrindo cada operacao e a publicacao de evento
- [x] T034 [US2] Criar `RegraControllerIT` com Testcontainers cobrindo o ciclo completo, `409` em chave duplicada e `422` em natureza hibrida
- [x] T035 [US2] Criar migration `V2__semeia_regras.sql` com as 7 regras do conjunto `PADRAO` e os conjuntos `PIX`, `CARTAO` e `TED` do enunciado

**Checkpoint**: regras persistem e o CRUD funciona ponta a ponta com massa semeada.

---

## Phase 4: User Story 3 — Composicao por chave de sobreposicao (P1)

**Story Goal**: conjunto efetivo de um tipo resulta da composicao entre `PADRAO` e o especifico.

**Independent Test**: consultar `GET /v1/regras?tipoTransacao=CARTAO&efetivo=true` e verificar
que `faixa_valor_1` vem de `CARTAO` e as demais faixas vem de `PADRAO`.

- [x] T036 [US3] Criar `ConjuntoEfetivo` em `dominio/composicao/` implementando o merge por chave com `HashMap`: mesma chave substitui, chave nova acrescenta, padrao sem par permanece, regra sem chave e sempre aditiva
- [x] T037 [P] [US3] Criar `ConjuntoEfetivoTest` com um caso nomeado por cenario do enunciado: substituicao por chave, heranca do padrao, acrescimo de chave nova, aditiva sem chave, e tipo sem conjunto especifico
- [x] T038 [US3] Criar `Escada` em `dominio/regra/Escada.java` com `NavigableMap<BigDecimal, Regra>` e a faixa sem teto como fallback, resolvendo por `ceilingEntry`
- [x] T039 [US3] Implementar em `Escada` as invariantes de carga: exatamente uma faixa sem teto, limites superiores distintos, limites positivos — lancando excecao de dominio na violacao
- [x] T040 [P] [US3] Criar `EscadaTest` cobrindo resolucao em cada faixa, valor exatamente no limite, valor acima do maior teto, e as tres invariantes violadas
- [x] T040a [P] [US3] Criar `MultiplasEscadasTest` verificando que escadas de nomes distintos sao independentes, que as invariantes valem por escada e nao globalmente, e que escada sem regra no conjunto efetivo simplesmente nao pontua (FR-023a)
- [x] T041 [US3] Criar `CacheRegras` em `infraestrutura/cache/` mantendo `Map<tipoTransacao, ConjuntoEfetivo>`, construindo as escadas na carga e **preservando o cache anterior** quando a carga falhar
- [x] T042 [P] [US3] Criar `CacheRegrasTest` verificando que carga invalida preserva o estado anterior e nunca deixa o cache vazio
- [x] T043 [US3] Expor `GET /v1/regras?efetivo=true` em `RegraController`, devolvendo cada regra com o campo `origem` (`PADRAO` ou o tipo)
- [x] T044 [US3] Criar `ComposicaoRegrasIT` validando via HTTP a heranca de `CARTAO` e de `TED` conforme o exemplo do enunciado

**Checkpoint**: a composicao por chave e observavel pela API e coberta por testes nomeados.

---

## Phase 5: User Story 1 — Obter decisao de risco (P1) — MVP

**Story Goal**: fluxo completo de analise, da requisicao do cliente a decisao.

**Independent Test**: com regras semeadas e listas carregadas, enviar transacao e receber
apenas a decisao, coerente com as regras.

### Avaliacao e score (motor-decisao)

- [x] T045 [P] [US1] Criar `ContextoAvaliacao` em `dominio/avaliacao/` com dados da transacao e os quatro sinais de lista
- [x] T046 [US1] Criar interface `AvaliadorCondicao` e uma implementacao por operador em `dominio/avaliacao/operador/` (Strategy)
- [x] T047 [US1] Criar `RegistroAvaliadores` com `Map<Operador, AvaliadorCondicao>` para dispatch O(1) em `dominio/avaliacao/`
- [x] T048 [US1] Criar `RegistroExtratoresCampo` com `Map<Campo, Function<ContextoAvaliacao, Object>>` em `dominio/avaliacao/`
- [x] T049 [US1] Criar `AvaliadorRegra` (Composite) em `dominio/avaliacao/` combinando condicoes por `E`/`OU` num unico booleano, garantindo que `OU` aplique a acao uma unica vez
- [x] T050 [P] [US1] Criar `AvaliadorRegraTest` cobrindo `E` com todas verdadeiras, `E` com uma falsa, `OU` com uma verdadeira, `OU` com ambas verdadeiras (acao aplicada uma vez) e operador logico omitido assumindo `E`
- [x] T051 [US1] Criar `CalculadoraScore` em `dominio/` somando escadas e regras condicionais e aplicando `max(1, soma)` uma unica vez ao final
- [x] T052 [P] [US1] Criar `CalculadoraScoreTest` cobrindo soma cumulativa, subtracao levando a zero ou negativo, piso aplicado apenas no fim, e conjunto de regras vazio resultando em score 1
- [x] T053 [US1] Criar `CalcularScoreUseCase` em `aplicacao/` lendo o conjunto efetivo do cache e devolvendo score mais regras acionadas
- [x] T054 [US1] Criar `ScoreController` expondo `POST /v1/scores` conforme contracts/motor-decisao.md, e `503` quando nao houver cache de regras valido
- [x] T055 [US1] Criar `ScoreControllerIT` validando os cenarios de score do enunciado ponta a ponta

### Orquestracao (api-analise-risco)

- [x] T056 [P] [US1] Criar value objects de dominio `Transacao`, `Decisao` e `ClassificacaoRisco` em `analiserisco/dominio/`, com validacao na construcao
- [x] T057 [P] [US1] Criar `TransacaoTest` cobrindo CPF invalido, valor zero, valor negativo, valor com tres casas decimais e normalizacao do tipo de transacao para maiusculas
- [x] T058 [US1] Criar as portas `ConsultaListasPort` e `CalculoScorePort` em `aplicacao/porta/`
- [x] T059 [US1] Criar `ClienteListas` em `infraestrutura/listas/` com `RestClient`, timeouts explicitos e ACL traduzindo o DTO do vizinho para `ResultadoListas`
- [x] T060 [US1] Implementar em `ClienteListas` a degradacao: qualquer falha ou timeout resulta em `ResultadoListas` com todos os sinais falsos e marcador `consultaDegradada`
- [x] T061 [US1] Criar `ClienteMotorDecisao` em `infraestrutura/motor/` com `RestClient`, timeouts e ACL, propagando falha como excecao de dominio (fail-closed)
- [x] T062 [US1] Criar `AnalisarRiscoUseCase` em `aplicacao/` orquestrando listas, score, classificacao e decisao, e publicando o evento de auditoria
- [x] T063 [P] [US1] Criar `AnalisarRiscoUseCaseTest` com portas dubladas, cobrindo aprovacao, negativa, degradacao de listas e fail-closed do motor
- [x] T064 [US1] Criar DTOs de analise em `infraestrutura/web/dto/`, com a resposta contendo **apenas** o campo `decisao`
- [x] T065 [US1] Criar `AnaliseRiscoController` expondo `POST /v1/analises-risco`, com `503` mais `Retry-After` no fail-closed
- [x] T066 [US1] Criar `AnaliseRiscoControllerIT` com WireMock dublando listas e motor, cobrindo aprovacao, negativa, degradacao, fail-closed e validacao de entrada
- [x] T067 [US1] Criar teste que falha se a resposta de analise contiver `score` ou `classificacao` em corpo ou cabecalho, em cenario de sucesso e de erro (FR-008)

**Checkpoint**: MVP funcional — cliente obtem decisao ponta a ponta.

---

## Phase 6: User Story 4 — Consultar listas (P2)

**Story Goal**: consulta das tres variaveis em uma chamada, distinguindo permissiva, restritiva, ambas e nenhuma.

**Independent Test**: carregar entradas conhecidas e consultar, verificando os quatro estados.

- [x] T068 [P] [US4] Criar dominio de listas em `listas/dominio/`: `TipoVariavel`, `Pertinencia`, `EntradaLista`, `ResultadoConsulta`
- [x] T069 [P] [US4] Criar `PertinenciaTest` verificando que situacao diferente de `ATIVA` e que `expiraEm` no passado tornam a pertinencia inefetiva
- [x] T070 [US4] Criar `ClienteDynamoDb` (`@Configuration`) em `infraestrutura/dynamodb/` construindo `DynamoDbEnhancedClient` com endpoint e credenciais por propriedade
- [x] T071 [US4] Criar as classes de item `ItemListaCpf`, `ItemListaIp` e `ItemListaDispositivo` com `@DynamoDbBean` em `infraestrutura/dynamodb/`
- [x] T072 [US4] Criar `BootstrapTabelas` idempotente criando as tres tabelas e habilitando TTL em `listas_ip`, ativado por propriedade `app.dynamodb.criar-tabelas`
- [x] T073 [US4] Criar `RepositorioListasDynamoDb` consultando as tres variaveis em um unico `BatchGetItem` sobre as tres tabelas
- [x] T074 [US4] Implementar no repositorio o filtro de expiracao na leitura, tratando entrada expirada como ausente independentemente do expurgo fisico
- [x] T075 [US4] Criar `ConsultarListasUseCase` em `aplicacao/`
- [x] T076 [US4] Criar DTOs e `ConsultaListasController` expondo `POST /v1/consultas-listas` conforme contracts/servico-listas.md
- [x] T077 [P] [US4] Criar `ConsultarListasUseCaseTest` com repositorio dublado cobrindo os quatro estados de pertinencia
- [x] T078 [US4] Criar `ConsultaListasIT` com DynamoDB Local via Testcontainers, cobrindo CPF em ambas as listas, ausencia total, IP restritivo e IP expirado

**Checkpoint**: consulta de listas operacional e integrada a US1.

---

## Phase 7: User Story 5 — Manter listas atualizadas (P2)

**Story Goal**: carga de entradas com efeito imediato nas consultas.

**Independent Test**: consultar (ausente), carregar, consultar de novo (presente), sem restart.

- [x] T079 [US5] Criar `CarregarListasUseCase` em `aplicacao/` gravando entradas de forma idempotente por variavel
- [x] T080 [US5] Criar DTOs de carga e `CargaListasController` expondo `PUT /v1/listas` protegido por `X-Api-Key`
- [x] T081 [US5] Implementar as validacoes de coerencia por tipo: `permissiva` apenas para CPF, `expiraEm` apenas para IP e no futuro, limite de 500 entradas por lote
- [x] T081a [US5] Criar `ValidadorCpf` tambem em `modulos/servico-listas/src/main/java/br/com/acme/listas/dominio/`, aplicado na carga e na consulta. Duplicacao deliberada: nao existe modulo compartilhado, e a alternativa seria aceitar CPF invalido nas listas — entrada que nunca casaria com a consulta validada da api-analise-risco
- [x] T082 [P] [US5] Criar `CarregarListasUseCaseTest` cobrindo cada validacao rejeitada e a sobrescrita idempotente
- [x] T083 [US5] Criar `CargaListasIT` validando o ciclo consultar-carregar-consultar e o `401` sem credencial
- [x] T084 [US5] Criar o script de seed usado pelo container do compose, populando a massa dos cenarios do quickstart via `PUT /v1/listas`

**Checkpoint**: listas populaveis; cenarios do quickstart reproduziveis.

---

## Phase 8: User Story 6 — Faixas de score configuraveis (P2)

**Story Goal**: faixas e politica de decisao alteraveis em runtime, propagadas por evento.

**Independent Test**: transacao de risco medio aprovada; alterar faixa para negar; mesma transacao negada.

- [x] T085 [P] [US6] Criar `FaixaScore` e `TabelaFaixas` em `analiserisco/dominio/`, com `NavigableMap<Integer, FaixaScore>` e classificacao por `ceilingEntry`
- [x] T086 [US6] Implementar em `TabelaFaixas` as invariantes de carga (uma faixa sem teto, limites distintos e positivos)
- [x] T087 [P] [US6] Criar `TabelaFaixasTest` cobrindo classificacao nos limites 399/400/699/700, score 1, e cada invariante violada
- [x] T088 [US6] Criar migration `V1__cria_schema_faixas.sql` e `V2__semeia_faixas.sql` com as tres faixas padrao
- [x] T089 [US6] Criar entidade JPA, repositorio e mapper de faixa em `infraestrutura/persistencia/`
- [x] T090 [US6] Criar `CacheFaixas` em `infraestrutura/cache/` preservando o estado anterior em carga invalida
- [x] T091 [US6] Criar `GerenciarFaixasUseCase` com consulta e substituicao integral, publicando `faixas.atualizadas`
- [x] T092 [US6] Criar `FaixaScoreController` expondo `GET` e `PUT /v1/faixas-score` protegidos, com `422` em invariante violada
- [x] T093 [P] [US6] Criar `GerenciarFaixasUseCaseTest` cobrindo substituicao valida, rejeicao por invariante e preservacao da configuracao vigente
- [x] T094 [US6] Criar `FaixaScoreControllerIT` validando alteracao de politica e efeito na decisao

**Checkpoint**: politica de risco alteravel sem deploy.

---

## Phase 9: Invalidacao de cache por Kafka (transversal a US2, US3 e US6)

**Purpose**: propagar alteracoes de configuracao a todas as replicas.

- [x] T095 Criar `@Bean NewTopic` para `regras.atualizadas` em `motor-decisao` e para `faixas.atualizadas` e `decisoes.registradas` em `api-analise-risco`
- [x] T096 Criar `PublicadorEventoRegras` em `modulos/motor-decisao/infraestrutura/mensageria/`, acionado por toda escrita do CRUD
- [x] T097 Criar `ConsumidorEventoRegras` com `group.id` unico por instancia (sufixo de hostname) e `auto-offset-reset=latest`, disparando recarga do `CacheRegras`
- [x] T098 [P] Criar `PublicadorEventoFaixas` e `ConsumidorEventoFaixas` em `api-analise-risco` com a mesma estrategia de `group.id`
- [x] T099 Criar `InvalidacaoCacheRegrasIT` com Kafka via Testcontainers: alterar regra, aguardar o evento e confirmar que a proxima avaliacao usa a regra nova
- [x] T100 [P] Criar `InvalidacaoCacheFaixasIT` equivalente para faixas
- [x] T101 Configurar `readiness` como `DOWN` enquanto o cache inicial nao carregar, nos dois servicos com cache

**Checkpoint**: alteracao de configuracao propaga em segundos sem restart.

---

## Phase 10: User Story 7 — Auditar decisoes (P3)

**Story Goal**: trilha completa e recuperavel de cada analise, fora do caminho critico.

**Independent Test**: submeter transacao, consultar a trilha e conferir score, classificacao, decisao e regras acionadas.

- [x] T102 [P] [US7] Criar `TrilhaDecisao` em `auditoria/dominio/`
- [x] T103 [US7] Criar migration `V1__cria_schema_trilha.sql` com `trilha_decisao`, `uk_trilha_correlacao` e indices por CPF e por data
- [x] T104 [US7] Criar entidade JPA, repositorio e mapper de trilha, com `regras_acionadas` e `resultado_listas` em `JSONB`
- [x] T105 [US7] Criar `PublicadorEventoDecisao` em `api-analise-risco`, assincrono, cuja falha e registrada em log mas **nao** impede a resposta ao cliente
- [x] T106 [US7] Criar `ConsumidorEventoDecisao` em `servico-auditoria` com `group.id` compartilhado e `auto-offset-reset=earliest`
- [x] T107 [US7] Criar `RegistrarTrilhaUseCase` tratando violacao de unicidade de correlacao como sucesso (idempotencia no consumo)
- [x] T108 [US7] Criar `TrilhaController` expondo `GET /v1/trilhas` com filtro por CPF e por correlacao, protegido por `X-Api-Key`
- [x] T109 [P] [US7] Criar `RegistrarTrilhaUseCaseTest` cobrindo registro novo e reentrega do mesmo evento sem duplicar
- [x] T110 [US7] Criar `AuditoriaIT` com Kafka e Postgres via Testcontainers, validando o percurso do evento ate a trilha
- [x] T111 [US7] Criar teste verificando que a decisao e entregue ao cliente mesmo com o `servico-auditoria` indisponivel (FR-035)

**Checkpoint**: toda decisao auditavel; auditoria nao afeta o caminho critico.

---

## Phase 11: Polish & Cross-Cutting

- [x] T112 Criar `docs/adr/0001-topologia-quatro-servicos.md` registrando a decisao, as alternativas e o motivo da rejeicao
- [x] T113 [P] Criar `docs/adr/0002-escada-por-limite-superior.md`
- [x] T114 [P] Criar `docs/adr/0003-conjunto-padrao-como-dado.md`
- [x] T115 [P] Criar `docs/adr/0004-invalidacao-cache-por-kafka.md`
- [x] T116 [P] Criar `docs/adr/0005-dynamodb-tres-tabelas.md` com o racional contra single-table design
- [x] T117 [P] Criar `docs/adr/0006-degradacao-e-fail-closed.md`
- [x] T118 [P] Criar `docs/adr/0007-ubiquitous-language-portugues.md`
- [x] T119 [P] Criar `docs/adr/0008-sem-modulo-compartilhado.md`
- [x] T120 Criar `README.md` na raiz com visao geral, diagramas Mermaid de componentes e de sequencia, instrucoes de execucao, roteiro `curl`, tabela de Big O e a lista de premissas
- [x] T120a Criar `MascaramentoLogIT` capturando a saida de log durante uma analise real e falhando se CPF, IP ou UUID de dispositivo aparecerem completos (SC-010 — o mascarador ja e testado isoladamente em T015, mas isso nao prova que ele esta aplicado no caminho real)
- [x] T120b Criar `LatenciaAnaliseIT` medindo o percentil 95 de uma serie de analises contra o compose e registrando o resultado no README (SC-001). Nao e teste de carga: e uma medicao reproduzivel da meta declarada
- [x] T121 Verificar que o gate de cobertura passa com no minimo 90% global: `./gradlew clean build jacocoTestCoverageVerification`
- [x] T122 Executar os dez cenarios do quickstart.md contra o compose e corrigir divergencias
- [x] T123 Revisar os quatro modulos removendo comentario desnecessario, confirmando que nenhum identificador tem acento e que nenhum valor monetario usa `double` ou `float` (FR-003a)
- [x] T124 Confirmar por inspecao dos `build.gradle` que o pacote `dominio` de cada modulo nao depende de framework (Principio I)

---

## Phase 12: Gerador de trafego (servico auxiliar)

**Purpose**: medir SC-001 (150 ms no p95), que ate aqui era meta declarada e nunca verificada, e
permitir demonstracao do sistema sob carga.

> As tasks T040a, T120a e T120b, criadas pelo `/speckit-analyze`, foram implementadas nesta fase.

- [x] T125 Criar o modulo `gerador-trafego` no monorepo (porta 8084) com `settings.gradle` e `build.gradle`
- [x] T126 [P] Criar `GeradorCpf` em `dominio/`, calculando digito verificador e rejeitando sequencias repetidas
- [x] T127 [P] Criar `CalculadoraPercentil` em `dominio/`, por nearest-rank, com o metodo declarado
- [x] T128 [P] Criar `PerfilTrafego` em `dominio/`, com distribuicao calibrada por tipo, faixa de valor e presenca em listas
- [x] T129 Criar `ResultadoCarga` em `dominio/`, acumulando latencias, decisoes e erros de forma concorrente
- [x] T130 Criar `ExecutarCargaUseCase` em `aplicacao/`, com virtual threads e uma carga por vez
- [x] T131 Criar `ClienteAnaliseRiscoHttp` traduzindo falha em motivo agrupavel, sem interromper a carga
- [x] T132 Criar `CargaController` com `POST`/`GET`/`DELETE /v1/cargas` protegidos por `X-Api-Key`
- [x] T133 [P] Criar `GeradorCpfTest` verificando que 1.000 CPFs gerados passam pelo `ValidadorCpf`
- [x] T134 [P] Criar `CalculadoraPercentilTest` com valores conhecidos e casos de borda
- [x] T135 [P] Criar `PerfilTrafegoTest` com semente fixa, provando que as quatro faixas e os tres tipos sao exercitados
- [x] T136 [P] Criar `ExecutarCargaUseCaseTest` com cliente dublado
- [x] T137 Criar `GeradorTrafegoIT` com WireMock, cobrindo controller, filtros, tratador de erro e client
- [x] T138 Adicionar o servico ao `docker-compose.yml`, ocioso por padrao, e incluir o modulo no `infra/Dockerfile`
- [x] T139 Fixar `logging.level.org.springframework.web: INFO` nos servicos que recebem dados pessoais
- [x] T140 Criar `docs/adr/0009-gerador-trafego-como-modulo-java.md`
- [x] T141 Medir o p95 real contra o ambiente do compose e registrar no README

**Checkpoint**: SC-001 verificavel por comando; p95 medido em 129 ms (JVM fria) e 78 ms (aquecida).

---

## Dependencies

```text
Phase 1 (Setup)
   ↓
Phase 2 (Foundational)  ← bloqueia todas as stories
   ↓
Phase 3 (US2: CRUD regras)  ← base do motor
   ↓
Phase 4 (US3: composicao)   ← precisa das regras persistidas
   ↓
Phase 5 (US1: analise)  ← MVP; precisa de score, portanto de US2 e US3
   ↓
   ├── Phase 6 (US4: consulta listas)   ─┐
   │      ↓                              │ independentes entre si
   │   Phase 7 (US5: carga listas)      ─┤
   ├── Phase 8 (US6: faixas)            ─┘
   ↓
Phase 9 (Kafka: invalidacao)  ← completa US2, US3 e US6
   ↓
Phase 10 (US7: auditoria)
   ↓
Phase 11 (Polish)
```

**Nota sobre a ordem**: a spec prioriza US1 como P1, mas a implementacao comeca por US2 e US3.
Nao e contradicao — US1 depende de existir score, e score depende de regras persistidas e
compostas. A prioridade expressa valor de negocio; a ordem expressa dependencia tecnica.

Ate a Phase 5, a US1 pode ser exercitada com WireMock dublando o servico de listas; a Phase 6
substitui o duble pela implementacao real.

## Parallel Opportunities

| Fase | Tasks paralelizaveis |
|---|---|
| Setup | T005–T008 (um `build.gradle` por modulo) |
| Foundational | T012–T015, T017–T020, T022 |
| US2 | T023–T024, T026, T033 |
| US3 | T037, T040, T042 |
| US1 | T045, T050, T052, T056–T057, T063 |
| US4 | T068–T069, T077 |
| Polish | T113–T119 (um ADR por arquivo) |

## Implementation Strategy

**MVP** = Phases 1 → 2 → 3 → 4 → 5. Entrega o fluxo de decisao completo, com o servico de
listas dublado. E o menor conjunto que demonstra o desafio: orquestracao, composicao de regras
por chave, calculo de score com piso e classificacao por faixa.

**Incremento 2** = Phases 6 → 7. Substitui o duble pelo servico de listas real com DynamoDB.

**Incremento 3** = Phases 8 → 9. Configurabilidade em runtime com propagacao por evento — e o
que sustenta o requisito de alterar regras e politica sem deploy.

**Incremento 4** = Phase 10. Auditoria.

**Fechamento** = Phase 11. ADRs, README e verificacao dos portoes.

**Total**: 145 tasks · US1 23 · US2 13 · US3 10 · US4 11 · US5 7 · US6 10 · US7 10 ·
transversais 61

## Correcoes aplicadas pelo /speckit-analyze (2026-07-29)

| Achado | Correcao |
|---|---|
| FR-029 dizia "apenas a pontuacao", conflitando com `regrasAcionadas` no contrato do motor | FR-029 reescrito: proibe classificacao e decisao, permite o detalhamento para auditoria, veda a propagacao ao cliente |
| T011 dizia "4 bancos ou 4 schemas" | corrigido para 3 schemas — `servico-listas` usa DynamoDB |
| FR-023a (multiplas escadas) sem teste | T040a |
| CPF validado so em `api-analise-risco` | T081a valida tambem em `servico-listas` |
| SC-010 verificado apenas manualmente | T120a — teste automatizado sobre a saida de log |
| SC-001 (150 ms p95) sem verificacao | T120b — medicao reproduzivel |
| FR-003a sem verificacao de ausencia de `double` | incluido em T123 |

Cobertura apos correcoes: **46/46 requisitos funcionais (100%)** e **12/12 criterios de sucesso (100%)**.
As tasks T040a, T120a e T120b, geradas por essas correcoes, foram implementadas na Phase 12.
