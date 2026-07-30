# Decisoes tomadas sem consulta previa

Este arquivo registra as decisoes tomadas durante a execucao autonoma do fluxo Spec Kit, apos
autorizacao para prosseguir sem aguardar confirmacao a cada passo. As decisoes acordadas
previamente estao nos ADRs em `docs/adr/`.

Cada item traz a decisao, o motivo e o que seria necessario considerar para reverte-la. O objetivo
e que nenhuma escolha feita sem consulta fique sem rastro — quem revisa consegue discordar de
qualquer uma delas com o contexto completo em maos.

---

## Fase: clarify

### 1. Precisao monetaria limitada a duas casas decimais

**Decisao**: `valorTransacao` aceita no maximo 2 casas decimais; mais que isso e rejeitado com
`400`. Toda aritmetica monetaria usa decimal exato (`BigDecimal`), nunca ponto flutuante.

**Motivo**: centavo e a menor unidade do real. Aceitar fracoes de centavo introduziria
arredondamento silencioso exatamente na comparacao com os limites de faixa, que e onde o
sistema decide quantos pontos somar.

**Efeito colateral que corrigi**: a premissa original justificava a modelagem por limite
superior citando o vao entre `300,00` e `300,01` do enunciado. Com a entrada limitada a
centavos, esse vao e inalcancavel e o argumento caia. Reescrevi a justificativa em torno do
ganho real, que e outro e mais forte: ao compor conjunto padrao com especifico por chave,
nenhuma redefinicao de faixa pode gerar lacuna ou sobreposicao.

**Para reverter**: aceitar mais casas decimais nao quebra nada estruturalmente, mas exige
definir a politica de arredondamento na comparacao de faixas.

### 2. Tipo de transacao como texto livre, nao enumeracao

**Decisao**: `tipoTransacao` e texto livre normalizado para maiusculas, com limite de tamanho.
Nao existe enumeracao fechada de tipos.

**Motivo**: o enunciado escreve "PIX, CARTAO, TED, etc." e o "etc." e a pista. Enumeracao
fechada exigiria alterar codigo e fazer deploy para cada tipo novo, o que contraria
diretamente o principio de configuracao como dado e o criterio de sucesso de adicionar tipos
sem tocar em codigo. Tipo desconhecido nao e erro: aplica-se apenas o conjunto `PADRAO`.

**Para reverter**: enumeracao daria validacao mais forte na entrada, ao custo de deploy por
tipo novo.

### 3. Chave de sobreposicao unica por (tipo de transacao, chave)

**Decisao**: restricao de unicidade no banco. Duas regras com a mesma chave no mesmo conjunto
sao rejeitadas. Regras sem chave nao entram na restricao e podem repetir livremente.

**Motivo**: se duas regras do tipo `CARTAO` declarassem a chave `faixa_valor_1`, a composicao
ficaria indeterminada — nao haveria como saber qual delas substitui a regra padrao. E um
estado que o modelo nao deve permitir representar.

### 4. Multiplas escadas independentes

**Decisao**: o modelo suporta N escadas identificadas por nome, nao apenas a de valor. As
invariantes (exatamente uma faixa sem teto, limites superiores distintos) valem por escada.

**Motivo**: o enunciado precisa de uma escada so, mas generalizar custa zero — a escada ja e um
campo da regra. Habilita, por exemplo, escada por horario ou por volume recente sem mudanca de
modelo. Escada sem regras no conjunto efetivo simplesmente nao pontua.

### 5. CPF apenas com digitos, e rejeicao de sequencias repetidas

**Decisao**: entrada aceita exclusivamente 11 digitos sem mascara. Valida digitos verificadores
**e** rejeita sequencias de digito repetido.

**Motivo**: o enunciado e explicito ("apenas numeros"). Aceitar mascara criaria duas
representacoes da mesma chave nas listas, com risco de um CPF cadastrado com pontuacao nunca
casar na consulta. E o detalhe que costuma passar: `11111111111` **satisfaz** o calculo do
digito verificador do modulo 11, mas nao e CPF valido — validar so o algoritmo deixaria passar
onze CPFs invalidos.

---

## Fase: implementacao

### 6. Comparacao ordinal e proibida sobre campos booleanos

**Decisao**: no `RegistroAvaliadores`, operadores ordinais (`MAIOR_QUE`, `MENOR_QUE`,
`MAIOR_OU_IGUAL`, `MENOR_OU_IGUAL`) nunca acionam quando algum dos lados e booleano. Apenas
`IGUAL` e `DIFERENTE` se aplicam a campo booleano.

**Motivo**: um teste expos o problema. `Boolean` implementa `Comparable` em Java, e
`Boolean.TRUE.compareTo(Boolean.FALSE)` devolve `1`. Sem bloqueio explicito, a regra sem sentido
`CPF_EM_LISTA_PERMISSIVA MAIOR_QUE 5` acionava para **todo** CPF em lista permissiva — o valor
`"5"` coage para `Boolean.FALSE`, e `true > false` e verdadeiro.

Isso e um falso positivo silencioso: a regra pontuaria sem que ninguem tivesse pedido, e o
sintoma apareceria como score inexplicavelmente alto em producao. Como decidimos validar apenas
a estrutura do cadastro (item 5 acima e premissa do README), regras semanticamente inuteis
**podem** ser cadastradas — logo elas precisam ser inertes, nao ativas por acidente.

**Alternativa rejeitada**: validar compatibilidade campo×operador no cadastro. Ja havia sido
descartada em decisao anterior (ver premissa 6 do README); e ainda assim nao bastaria, porque
regras gravadas antes da validacao continuariam acionando.

### 7. `IGUAL` compara por `compareTo`, nao por `equals`

**Decisao**: a igualdade tambem passa por `Comparable.compareTo` quando os tipos permitem,
caindo em `equals` apenas como fallback.

**Motivo**: `new BigDecimal("300.00").equals(new BigDecimal("300"))` e **false** — `equals` de
`BigDecimal` considera a escala. Uma condicao `VALOR_TRANSACAO IGUAL 300` nunca seria satisfeita
por uma transacao de `300.00`, que e exatamente como o valor chega do JSON. O bug seria
invisivel: a regra existiria, pareceria correta e nunca acionaria.

### 8. Spring Boot 4 modularizou as autoconfiguracoes — starters explicitos por integracao

**Decisao**: declarar `spring-boot-starter-flyway`, `spring-boot-starter-kafka` e
`spring-boot-starter-json` explicitamente, em vez de depender de `org.flywaydb:flyway-core` e
`org.springframework.kafka:spring-kafka` diretos.

**Motivo**: descoberto na pratica, com o sistema subindo. Em Spring Boot 3, ter `flyway-core` no
classpath bastava para a autoconfiguracao ativar. Em Boot 4 as autoconfiguracoes foram divididas em
modulos proprios (`spring-boot-flyway`, `spring-boot-kafka`, `spring-boot-jackson`), que os
starters trazem. Sem eles, a biblioteca esta no classpath mas **nada a configura** — e o sintoma
nao e um erro claro:

- Flyway: nenhuma migration executava, e o JPA falhava depois com `missing table [condicao]`. O log
  nao mencionava Flyway em nenhuma linha, porque ele simplesmente nao existia como bean.
- Kafka: `KafkaProperties` nao estava nem no classpath.

**Como apareceu**: apenas ao rodar o `docker compose`. Compilava e os testes unitarios passavam,
porque nenhum deles subia o contexto Spring completo. E um argumento concreto a favor de exigir
teste de integracao — o gate de cobertura sozinho nao teria pego isso.

### 9. Spring Boot 4 usa Jackson 3 — pacote `tools.jackson`

**Decisao**: `tools.jackson.databind.ObjectMapper` e `tools.jackson.core.JacksonException`.

**Motivo**: Boot 4.1 traz `tools.jackson.core:jackson-databind:3.1.4`. Nao existe bean de
`com.fasterxml.jackson.databind.ObjectMapper` para injetar — o contexto falhava com
`No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'`.

Detalhe util: as **anotacoes** seguem em `com.fasterxml.jackson.core:jackson-annotations:2.21`,
que o Jackson 3 usa. Portanto `@JsonRawValue` e companhia continuam vindo de
`com.fasterxml.jackson.annotation`. Codigo que mistura os dois pacotes esta correto.

### 10. `KafkaTemplate` declarado explicitamente

**Decisao**: cada servico produtor declara
`KafkaTemplate<String, Object>` construido a partir de `KafkaProperties.buildProducerProperties()`.

**Motivo**: a autoconfiguracao expoe `KafkaTemplate<?, ?>` e `ProducerFactory<?, ?>`. Injetar
`KafkaTemplate<String, EventoX>` falha na resolucao de generics. Construir a partir das propriedades
ja resolvidas mantem o `application.yml` como fonte unica de configuracao e evita raw types.

### 11. Consumidor de auditoria ignora o header de tipo do Kafka

**Decisao**: `spring.json.use.type.headers: false` mais `spring.json.value.default.type` no
`servico-auditoria`.

**Motivo**: o `JsonSerializer` do Spring Kafka grava o header `__TypeId__` com o nome da classe do
**produtor** — `br.com.acme.analiserisco...EventoDecisaoRegistrada`. Essa classe nao existe no
consumidor, precisamente porque nao ha modulo compartilhado. O consumo falhava com
`Class not found`.

Isso e o custo previsto da decisao de nao compartilhar modulo, e a correcao o transforma em ganho:
desserializar no tipo local significa que o produtor pode renomear ou mover a classe dele sem
quebrar este consumidor. O acoplamento fica no formato do JSON, nao no nome da classe Java.

### 12. Nome dos beans de `HealthIndicator`

**Decisao**: indicadores registrados como `regras` e `faixasScore`, nao `cacheRegras`/`cacheFaixas`.

**Motivo**: o nome do bean de um `HealthIndicator` vira a chave no `/actuator/health`, e
`@Component("cacheRegras")` colidia com o bean `CacheRegras`, derrubando a subida com
`ConflictingBeanDefinitionException`.

### 13. Lombok removido do projeto

**Decisao**: nenhum modulo usa Lombok. `record` e classes explicitas no lugar.

**Motivo**: o Principio I exige dominio verificavelmente livre de dependencia externa, e a
verificacao e por inspecao do `build.gradle`. Lombok e processador de anotacao, nao framework de
runtime, mas mante-lo obrigaria a explicar essa nuance na apresentacao sem ganho real — records
cobrem os value objects e as poucas classes restantes ficam mais legiveis explicitas.

---

## Fase: validacao

### 14. Falha de publicacao no Kafka nao propaga

**Decisao**: nos tres publicadores, a falha de envio e registrada em log mas nao propagada ao
chamador. Alem disso, quem atende uma alteracao administrativa **recarrega seu proprio cache de forma
sincrona**, sem depender do evento voltar pelo broker.

**Motivo**: um teste de integracao expos o problema. Com o broker indisponivel,
`kafkaTemplate.send()` lancava `KafkaException` e derrubava a resposta de um `POST /v1/regras` que
**ja havia persistido a regra**. O cliente concluiria que a operacao falhou, reenviaria, e receberia
`409` de chave duplicada — um erro que nao existe.

A consequencia de perder o evento e limitada e observavel: outras replicas seguem com cache anterior
ate a proxima alteracao, e o erro fica no log. A instancia que atendeu esta correta imediatamente.

### 15. DTO nao decide a natureza da regra — o dominio decide

**Decisao**: `RegraRequest.paraDominio()` repassa todos os campos a `Regra.reconstituir()`, em vez de
escolher entre `deEscada()` e `condicional()` conforme o que veio preenchido.

**Motivo**: outro teste expos isso. Na versao anterior, uma requisicao com `escada` **e**
`condicoes` era aceita com `201` — o DTO chamava `deEscada()`, que simplesmente **ignorava** as
condicoes. O cadastrante criaria uma regra acreditando que ela tem condicoes, e ela nao teria. Falha
silenciosa em configuracao de motor de regras e exatamente o tipo de bug caro.

Passando tudo ao dominio, a invariante de natureza rejeita o hibrido com `422`.

### 16. WireMock standalone, nao `wiremock-jetty12`

**Decisao**: `org.wiremock:wiremock-standalone:3.13.2` nos testes.

**Motivo**: correcao de uma escolha errada minha. O `research.md` optou por `wiremock-jetty12`
supondo que o standalone "conflitaria" — e o oposto e verdade. O standalone e **shaded**
precisamente para nao conflitar. O `wiremock-jetty12` tem como alvo Jetty 12.0 (ee10), enquanto o
Spring Boot 4 traz Jetty 12.1 (ee11); o teste falhava com
`NoSuchMethodError: Environment.ensure`.

### 17. Rebalance inicial do Kafka na subida do zero

**Observacao**, nao decisao: ao subir o ambiente do zero, o `servico-auditoria` registra
`NOT_COORDINATOR` e refaz o join do grupo por alguns segundos, antes de sincronizar. E o coordenador
do Kafka se estabelecendo, nao um defeito — o consumo funciona normalmente depois. O roteiro do
README aguarda readiness antes dos cenarios, o que absorve isso.

---

## Fase: gerador de trafego

### 18. Erro de marcacao em massa das tasks — correcao

**O que aconteceu**: ao encerrar a implementacao anterior, rodei um script que substituiu todos os
`- [ ] T` por `- [x] T` no `tasks.md`. Isso marcou como concluidas tres tasks que eu **nunca havia
implementado** — justamente as que o `/speckit-analyze` criou para fechar lacunas de cobertura:

| Task | Artefato | Requisito |
|---|---|---|
| `T040a` | `MultiplasEscadasTest` | FR-023a |
| `T120a` | `MascaramentoLogIT` | SC-010 |
| `T120b` | medicao de latencia | SC-001 |

Meu relatorio final afirmou "12/12 criterios de sucesso cobertos"; o correto naquele momento era
10/12. As tres foram implementadas nesta fase e a afirmacao passou a ser verdadeira.

**Licao**: automatizar a marcacao de conclusao e o oposto de verificar conclusao. Marcar deve
decorrer da verificacao, nunca substitui-la.

### 19. Nivel de log do Spring Web fixado em INFO

**Decisao**: `logging.level.org.springframework.web: INFO` explicitamente nos tres servicos que
recebem dados pessoais.

**Motivo**: descoberto pelo proprio `MascaramentoLogIT`, que falhou na primeira execucao. Em nivel
`DEBUG`, o Spring Web registra o corpo desserializado da requisicao:

```
Read "application/json" to [AnaliseRiscoRequest[cpf=52998224725, ip=203.0.113.42, idDispositivo=3f25...
```

Ou seja: **todo o mascaramento cuidadoso da aplicacao e contornado pelo framework** se alguem ligar
DEBUG — o que e rotina ao investigar um incidente em producao. O piso explicito impede isso, e o
teste falha se alguem remove-lo.

Foi o achado mais relevante desta fase, e nao teria aparecido sem um teste que inspeciona a saida
de log real. O teste unitario do mascarador passava — ele prova que o mascarador funciona, nao que
ele esta aplicado em todo caminho.

### 20. Gerador ocioso por padrao, uma carga por vez

**Decisao**: o `gerador-trafego` sobe com o compose mas nao gera trafego ate receber
`POST /v1/cargas`. Uma execucao por vez; a segunda recebe `409`.

**Motivo**: disparo automatico consumiria recursos sem ninguem pedir e encheria a trilha de
auditoria de registros sinteticos antes de qualquer demonstracao. E duas cargas simultaneas
misturariam amostras de latencia — o percentil resultante nao descreveria nenhuma das duas.

### 21. Percentil por nearest-rank, declarado explicitamente

**Decisao**: `ceil(p/100 × n)` sobre a amostra ordenada, sem interpolacao. Metodo documentado na
classe e fixado por teste com valores conhecidos.

**Motivo**: percentil e ambiguo — ha varias definicoes, e bibliotecas diferentes devolvem valores
distintos para a mesma amostra. Um "p95 de 129 ms" sem o metodo declarado nao e verificavel por
terceiros. Sem interpolacao, o valor reportado e sempre uma medicao que de fato aconteceu.

### 22. Erros de requisicao sao dado da medicao, nao falha da execucao

**Decisao**: o cliente HTTP do gerador traduz toda falha em motivo agrupavel (`HTTP_503`,
`RESPOSTA_VAZIA`) e a carga prossegue.

**Motivo**: interromper na primeira falha esconderia justamente o comportamento que interessa
observar sob volume. Derrubar o motor durante uma carga produz um relatorio com a proporcao de
`HTTP_503` — informacao util, e nao um teste abortado.

### 23. Dockerfile copia os cinco modulos, mesmo construindo um so

**Observacao**: o Gradle 9 exige que todo diretorio declarado em `settings.gradle` exista, ainda
que o build tenha um unico modulo como alvo. O build do gerador falhou com
`Configuring project ':gerador-trafego' without an existing directory` ate que o Dockerfile passasse
a copiar tambem os `build.gradle` e `src` dos demais.
