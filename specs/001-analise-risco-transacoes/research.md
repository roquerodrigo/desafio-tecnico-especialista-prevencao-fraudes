# Phase 0 — Research

**Feature**: Plataforma de Analise de Risco de Transacoes
**Date**: 2026-07-29

Toda versao abaixo foi verificada contra o Maven Central e contra a documentacao oficial,
nao assumida por memoria. Quatro incompatibilidades reais foram encontradas e resolvidas
antes de qualquer linha de codigo.

## Matriz de versoes

| Componente | Versao | Verificacao |
|---|---|---|
| Java | 25.0.1 LTS | `java -version` local; Boot 4.1 suporta 17–26 |
| Gradle | 9.6.1 | local; Boot 4.1 suporta 8.14+ e 9.x |
| Spring Boot | 4.1.0 | ultima estavel no Maven Central |
| Spring Framework | 7.0.8+ | exigido pelo Boot 4.1, resolvido transitivamente |
| spring-kafka | 4.1.0 | gerenciado pelo BOM do Boot |
| springdoc-openapi | 3.0.3 | linha 3.x e a de Boot 4 |
| AWS SDK v2 (`dynamodb-enhanced`) | 2.49.5 | ultima estavel |
| Flyway | gerenciada pelo BOM | evita divergencia com o driver |
| Testcontainers | 2.0.5 (via BOM) | ultima estavel |
| WireMock | 3.13.2 (`wiremock-jetty12`) | linha 4.x ainda em beta |
| JaCoCo | 0.8.15 | **minimo 0.8.14 para Java 25** |
| JUnit | 6.1.2 | gerenciado pelo BOM do Boot |

## Decisao 1 — JaCoCo 0.8.15, nunca inferior a 0.8.14

**Decision**: fixar JaCoCo em 0.8.15.

**Rationale**: Java 25 gera bytecode com class file major version 69. JaCoCo passou a
suportar Java 25 oficialmente apenas na 0.8.14; 0.8.13 tem suporte experimental e versoes
anteriores falham com `Unsupported class file major version 69`. Como o gate de cobertura de
90% e condicao de conclusao (Principio V), uma versao incompativel travaria a entrega inteira,
e o sintoma apareceria so ao rodar o relatorio — tarde.

**Alternatives considered**: compilar com `--release 21` para baixar o bytecode e liberar
JaCoCo antigo — rejeitado por descartar sem motivo os recursos da linguagem que justificam
escolher Java 25.

## Decisao 2 — Artefatos Testcontainers com prefixo `testcontainers-`

**Decision**: declarar `testcontainers-postgresql`, `testcontainers-kafka` e
`testcontainers-junit-jupiter`, via `testcontainers-bom:2.0.5`.

**Rationale**: Testcontainers 2.0 renomeou todos os artefatos de modulo com o prefixo
`testcontainers-`; apenas o artefato core manteve o nome. Verificado: `org.testcontainers:postgresql:2.0.5`
retorna 404 e `org.testcontainers:testcontainers-postgresql:2.0.5` retorna 200. Os pacotes
Java **nao** mudaram, portanto os `import` seguem iguais — o erro apareceria apenas na
resolucao de dependencia. A 2.0 tambem removeu o suporte a JUnit 4, o que nao nos afeta.

**Alternatives considered**: permanecer em Testcontainers 1.x — rejeitado por conflitar com
JUnit 6, que vem no BOM do Spring Boot 4.1.

## Decisao 3 — WireMock 3.13.2 com `wiremock-jetty12`

**Decision**: usar `org.wiremock:wiremock-jetty12:3.13.2`.

**Rationale**: Spring Boot 4.1 roda sobre Servlet 6.1 (Tomcat 11 / Jetty 12.1). O artefato
`wiremock-standalone` embute sua propria arvore de dependencias e conflita nesse contexto; a
variante `wiremock-jetty12` existe exatamente para alinhar com Jetty 12. A linha 4.x do
WireMock esta em `4.0.0-beta.38` e foi descartada por nao ser estavel.

**Alternatives considered**: `MockRestServiceServer` do Spring — mais leve e sem dependencia
extra, mas so intercepta no nivel do `RestClient`, sem exercitar serializacao HTTP real, o que
enfraqueceria os testes de ACL e de timeout.

## Decisao 4 — AWS SDK v2 direto, sem `spring-cloud-aws`

**Decision**: `software.amazon.awssdk:dynamodb-enhanced:2.49.5` com um `@Configuration`
proprio construindo o client.

**Rationale**: `io.awspring.cloud` 3.x tem como alvo Spring Boot 3.x. Depender dele em Boot 4
arriscaria conflito de autoconfiguracao por um ganho pequeno — o client do DynamoDB se
configura em cerca de dez linhas. Endpoint e credenciais vem de propriedade, o que permite
apontar para `amazon/dynamodb-local` em desenvolvimento e para a AWS real sem mudar codigo.

**Alternatives considered**: starter do Spring Cloud AWS — rejeitado pelo risco de
compatibilidade; LocalStack — removido do escopo por decisao do stakeholder (exige conta a
partir da `2026.03.0`).

## Decisao 5 — `NavigableMap` para resolucao de escada

**Decision**: `TreeMap<BigDecimal, Regra>` com `ceilingEntry(valor)` para faixas de valor, e a
mesma estrutura com `Integer` para faixas de score. A faixa sem teto fica fora do mapa, como
fallback.

**Rationale**: a semantica "primeira faixa cujo teto cobre o valor" e exatamente
`ceilingEntry`, em **O(log n)**. Uma lista com varredura linear seria O(n) e exigiria manter a
ordenacao a mao. O tipo da chave e `BigDecimal` — nunca `double` — porque a comparacao ocorre
em fronteira monetaria, onde erro de representacao binaria muda a faixa e portanto a pontuacao.

**Alternatives considered**: lista ordenada com busca binaria manual — mesma complexidade,
mais codigo e mais chance de erro de limite; varredura linear — aceitavel para 4 faixas, mas
descarta o argumento assintotico que o desafio pede explicitamente.

## Decisao 6 — Strategy + Registry para operadores, Composite para operador logico

**Decision**: `AvaliadorCondicao` como interface com uma implementacao por operador,
registradas em `Map<Operador, AvaliadorCondicao>`. As condicoes de uma regra sao combinadas
por um Composite que aplica `E` ou `OU`.

**Rationale**: dispatch por `Map` e O(1) e mantem o Open/Closed — operador novo e uma classe
nova mais uma entrada no registro, sem tocar no avaliador. Um `switch` sobre enum cresceria a
cada operador e concentraria a mudanca num ponto quente. O Composite resolve naturalmente o
requisito de a regra `OU` aplicar sua acao uma unica vez: a composicao produz um unico
booleano, e a acao consome esse booleano, nao as condicoes individuais.

**Alternatives considered**: biblioteca de expressoes (SpEL, MVEL, Drools) — rejeitada por
trazer motor de regras completo para um problema de sete regras, com custo de seguranca
(avaliacao de expressao vinda de banco) e de opacidade na apresentacao.

## Decisao 7 — Extracao de campo por Registry de extratores

**Decision**: `Map<Campo, Function<ContextoAvaliacao, Object>>` resolve o valor de cada campo
avaliavel, com `Campo` como enum fechado.

**Rationale**: mantem a DSL flexivel sem reflexao. Reflexao no caminho critico custa latencia
e transfere o erro de digitacao do cadastro para a producao; com enum, o campo invalido e
rejeitado na desserializacao. Campo novo custa uma constante e uma entrada no mapa.

**Alternatives considered**: resolucao por path reflexivo (`transacao.valor`) — mais flexivel
no papel, mas sem validacao possivel no cadastro e mais lenta.

## Decisao 8 — `group.id` por instancia para invalidacao de cache

**Decision**: cada instancia consumidora de `regras.atualizadas` e `faixas.atualizadas` usa um
`group.id` unico, derivado do hostname; `auto-offset-reset` fica em `latest`.

**Rationale**: com `group.id` compartilhado, o Kafka entrega cada mensagem a **uma** instancia
do grupo — exatamente o oposto do necessario. Invalidacao de cache exige fan-out: toda replica
precisa da sua copia. `latest` e correto aqui porque o Postgres e a fonte de verdade e o evento
e apenas um sinal para reler; reprocessar historico nao agrega.

**Alternatives considered**: `group.id` compartilhado — quebra o fan-out; `earliest` —
recarregaria varias vezes na subida sem beneficio.

## Decisao 9 — Filtro de expiracao na leitura das listas

**Decision**: alem do TTL declarado na tabela de IP, a aplicacao compara `expiraEm` com o
instante atual em toda leitura e trata entrada expirada como ausente.

**Rationale**: a documentacao da AWS afirma que o expurgo por TTL ocorre "within a few days"
da expiracao e recomenda filtrar itens expirados nas leituras. Portanto o filtro e obrigatorio
em producao, nao um contorno do emulador. Como usamos `GetItem`/`BatchGetItem`, onde
`FilterExpression` nao se aplica, o filtro fica na aplicacao. Efeito colateral util: o fato de
o DynamoDB Local nao expurgar deixa de importar.

## Decisao 10 — Uma unica chamada para as tres variaveis

**Decision**: `BatchGetItem` sobre as tres tabelas em uma requisicao.

**Rationale**: `BatchGetItem` aceita multiplas tabelas no mesmo `RequestItems`, o que preserva
o round-trip unico mesmo com a modelagem em tres tabelas separadas. Era o custo que se
imaginava ter ao rejeitar single-table design, e ele nao existe. Limite de 100 itens por
chamada, folgado para 3.

## Riscos residuais

| Risco | Mitigacao |
|---|---|
| Spring Boot 4.1 e recente; algum starter pode divergir | Versoes fixadas e verificadas; build roda a cada etapa |
| DynamoDB Local nao expurga por TTL | Filtro de expiracao na leitura (Decisao 9) |
| Primeira execucao de Testcontainers baixa imagens | Documentado no quickstart |
| Gate de 90% pode barrar classes de configuracao triviais | Exclusoes explicitas e justificadas no `build.gradle` |
