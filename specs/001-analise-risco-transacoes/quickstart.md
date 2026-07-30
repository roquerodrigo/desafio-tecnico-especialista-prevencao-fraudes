# Quickstart — validacao ponta a ponta

**Feature**: Plataforma de Analise de Risco de Transacoes

## Pre-requisitos

| Requisito | Versao | Verificacao |
|---|---|---|
| JDK | 25 | `java -version` |
| Docker + Compose | Compose v2+ | `docker compose version` |
| Gradle | dispensavel | o wrapper (`./gradlew`) baixa a versao correta |

A primeira execucao dos testes de integracao baixa imagens do Testcontainers (Postgres, Kafka,
DynamoDB Local). Docker precisa estar rodando.

## Subir o ambiente

```bash
docker compose up -d --build
```

Sobe, nesta ordem de dependencia: Postgres, DynamoDB Local, Kafka (KRaft), os quatro servicos e
o container de seed, que popula as listas e encerra.

Aguardar readiness:

```bash
for p in 8080 8081 8082 8083; do
  curl -sf "http://localhost:$p/actuator/health" | grep -q UP && echo "porta $p OK"
done
```

| Servico | Porta |
|---|---|
| api-analise-risco | 8080 |
| servico-listas | 8081 |
| motor-decisao | 8082 |
| servico-auditoria | 8083 |

Swagger UI em `http://localhost:<porta>/swagger-ui.html`.

## Cenarios de validacao

A chave administrativa padrao de desenvolvimento e `chave-desenvolvimento`, definida por
variavel de ambiente no compose.

### 1 — Aprovacao de transacao de baixo risco

```bash
curl -s -X POST http://localhost:8080/v1/analises-risco \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"52998224725","ip":"198.51.100.10",
       "idDispositivo":"11111111-1111-4111-8111-111111111111",
       "tipoTransacao":"PIX","valorTransacao":150.00}'
```

**Esperado**: `200` com `{"decisao":"APROVADA"}`.

Racional: PIX ate R$300 soma 300 pontos; nada em listas. Score 300 ⇒ `BAIXO` ⇒ aprova.

Confirmar que a resposta **nao** contem `score` nem `classificacao` — e o requisito FR-008.

### 2 — Negativa por valor alto

```bash
curl -s -X POST http://localhost:8080/v1/analises-risco \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"52998224725","ip":"198.51.100.10",
       "idDispositivo":"11111111-1111-4111-8111-111111111111",
       "tipoTransacao":"PIX","valorTransacao":25000.00}'
```

**Esperado**: `{"decisao":"NEGADA"}`. PIX acima de R$20.000 soma 700 ⇒ `ALTO` ⇒ nega.

### 3 — CPF em lista restritiva

O seed carrega `11144477735` em lista restritiva.

```bash
curl -s -X POST http://localhost:8080/v1/analises-risco \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"11144477735","ip":"198.51.100.10",
       "idDispositivo":"11111111-1111-4111-8111-111111111111",
       "tipoTransacao":"PIX","valorTransacao":1500.00}'
```

**Esperado**: `NEGADA`. Faixa 2 soma 400, lista restritiva soma 200 ⇒ 600? Nao: verifique o
conjunto efetivo do PIX com o cenario 7 antes de concluir — este cenario existe justamente para
ser conferido contra as regras cadastradas, nao contra um numero memorizado.

### 4 — Heranca por chave de sobreposicao

```bash
curl -s 'http://localhost:8082/v1/regras?tipoTransacao=CARTAO&efetivo=true' \
  -H 'X-Api-Key: chave-desenvolvimento' | jq
```

**Esperado**: `faixa_valor_1` com `origem: CARTAO` e 350 pontos; `faixa_valor_2`, `_3` e `_4` com
`origem: PADRAO`. Demonstra US3 de forma direta.

### 5 — Alterar regra e ver o efeito sem restart

```bash
# criar regra especifica para TED
curl -s -X POST http://localhost:8082/v1/regras \
  -H 'Content-Type: application/json' -H 'X-Api-Key: chave-desenvolvimento' \
  -d '{"chave":"faixa_valor_2","tipoTransacao":"TED","descricao":"TED ate R$5.000",
       "escada":"VALOR_TRANSACAO","limiteSuperior":5000.00,
       "acao":{"tipo":"SOMAR","pontos":800}}'

# mesma transacao TED passa a ser negada
curl -s -X POST http://localhost:8080/v1/analises-risco \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"52998224725","ip":"198.51.100.10",
       "idDispositivo":"11111111-1111-4111-8111-111111111111",
       "tipoTransacao":"TED","valorTransacao":1000.00}'
```

**Esperado**: `NEGADA`, sem nenhum restart. A propagacao ocorre por `regras.atualizadas`.

### 6 — Alterar politica de risco sem deploy

```bash
curl -s -X PUT http://localhost:8080/v1/faixas-score \
  -H 'Content-Type: application/json' -H 'X-Api-Key: chave-desenvolvimento' \
  -d '{"faixas":[
        {"classificacao":"BAIXO","limiteSuperior":399,"decisao":"APROVADA"},
        {"classificacao":"MEDIO","limiteSuperior":699,"decisao":"NEGADA"},
        {"classificacao":"ALTO","limiteSuperior":null,"decisao":"NEGADA"}]}'
```

Transacoes de risco medio passam a ser negadas. Reverta ao final para nao afetar os outros
cenarios.

### 7 — Degradacao com listas fora

```bash
docker compose stop servico-listas
```

Repita o cenario 1. **Esperado**: `200` com decisao normal — a analise prossegue sem o sinal de
listas. Depois:

```bash
docker compose start servico-listas
```

### 8 — Fail-closed com motor fora

```bash
docker compose stop motor-decisao
```

Repita o cenario 1. **Esperado**: `503` com `Retry-After` e corpo em `application/problem+json`.
Nao pode ser `200 NEGADA`: indisponibilidade tecnica nao e decisao de risco.

```bash
docker compose start motor-decisao
```

### 9 — Validacao de entrada

```bash
curl -s -X POST http://localhost:8080/v1/analises-risco \
  -H 'Content-Type: application/json' \
  -d '{"cpf":"11111111111","ip":"198.51.100.10",
       "idDispositivo":"11111111-1111-4111-8111-111111111111",
       "tipoTransacao":"PIX","valorTransacao":0}'
```

**Esperado**: `400` apontando **dois** erros — CPF (sequencia repetida, que passa no calculo do
digito verificador mas nao e valido) e valor (nao positivo).

### 10 — Trilha de auditoria e mascaramento

```bash
curl -s 'http://localhost:8083/v1/trilhas?cpf=52998224725' \
  -H 'X-Api-Key: chave-desenvolvimento' | jq '.[0]'
```

**Esperado**: registro com `score`, `classificacao`, `decisao`, `regrasAcionadas` e
`resultadoListas` — informacao que nunca chegou ao cliente.

```bash
docker compose logs api-analise-risco | grep -c '52998224725'
```

**Esperado**: `0`. O CPF aparece mascarado em log (`529****4725`), em claro apenas na trilha.

## Rodar os testes

```bash
./gradlew clean build
```

Executa testes unitarios e de integracao dos quatro modulos e aplica o gate de cobertura.

```bash
./gradlew jacocoTestCoverageVerification
```

Falha se a cobertura global ficar abaixo de 90%. Relatorios HTML em
`<modulo>/build/reports/jacoco/test/html/index.html`.

Somente testes unitarios, sem Docker:

```bash
./gradlew test --tests '*Test'
```

Testes de integracao (exigem Docker):

```bash
./gradlew test --tests '*IT'
```

## Derrubar

```bash
docker compose down -v
```

O `-v` remove os volumes: Postgres e DynamoDB voltam vazios e o seed roda de novo na proxima
subida.
