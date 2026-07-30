# Phase 1 — Data Model

**Feature**: Plataforma de Analise de Risco de Transacoes
**Date**: 2026-07-29

## Convencoes

- Identificadores Java e campos JSON: `camelCase`, portugues, sem acento
- Colunas Postgres: `snake_case`; atributos DynamoDB: `camelCase`
- Valor monetario: `BigDecimal` com escala 2, `NUMERIC(15,2)` no banco. Nunca `double`
- Instantes: `Instant` em UTC, `TIMESTAMPTZ` no banco

---

## motor-decisao

### Dominio

**`Regra`** — agregado raiz. Assume uma de duas naturezas, mutuamente exclusivas.

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `UUID` | identidade |
| `chave` | `String` (nullable) | chave de sobreposicao; nula = regra sempre aditiva |
| `tipoTransacao` | `String` | maiusculas; `PADRAO` designa o conjunto base |
| `descricao` | `String` | texto livre para o analista |
| `escada` | `String` (nullable) | nome da escada; nao nulo ⇒ regra de escada |
| `limiteSuperior` | `BigDecimal` (nullable) | teto da faixa; nulo em regra de escada = sem teto |
| `operadorLogico` | `OperadorLogico` (nullable) | `E` ou `OU`; ausente assume `E` |
| `condicoes` | `List<Condicao>` | vazia em regra de escada |
| `acao` | `Acao` | obrigatoria |
| `ativa` | `boolean` | permite desativar sem excluir |

**Invariante de natureza**: `escada` nao nulo ⇒ `condicoes` vazia e `limiteSuperior`
presente-ou-nulo-intencional. `escada` nulo ⇒ `condicoes` nao vazia. Uma regra nunca e escada
e condicional ao mesmo tempo — a construcao rejeita o hibrido.

**`Condicao`** — value object.

| Campo | Tipo |
|---|---|
| `campo` | `Campo` |
| `operador` | `Operador` |
| `valor` | `String` (coagido para o tipo do campo na avaliacao) |

**`Acao`** — value object: `tipo` (`SOMAR` \| `SUBTRAIR`) e `pontos` (`int` positivo).

**Enums**:

- `Campo`: `VALOR_TRANSACAO`, `TIPO_TRANSACAO`, `CPF_EM_LISTA_PERMISSIVA`,
  `CPF_EM_LISTA_RESTRITIVA`, `IP_EM_LISTA_RESTRITIVA`, `DISPOSITIVO_EM_LISTA_RESTRITIVA`
- `Operador`: `IGUAL`, `DIFERENTE`, `MAIOR_QUE`, `MAIOR_OU_IGUAL`, `MENOR_QUE`,
  `MENOR_OU_IGUAL`
- `OperadorLogico`: `E`, `OU`
- `TipoAcao`: `SOMAR`, `SUBTRAIR`

**`ContextoAvaliacao`** — value object com os dados da transacao mais o resultado das listas.
E o unico insumo dos extratores de campo.

**`ConjuntoEfetivo`** — derivado, nunca persistido. Resultado da composicao entre o conjunto
`PADRAO` e o do tipo concreto:

```text
efetivo = {}
para cada regra r em PADRAO:
    se r.chave != null:  efetivo[r.chave] = r
    senao:               efetivo.aditivas += r

para cada regra r em TIPO:
    se r.chave != null:  efetivo[r.chave] = r     # substitui ou acrescenta
    senao:               efetivo.aditivas += r

resultado = efetivo.values() ∪ efetivo.aditivas
```

Complexidade: **O(P + E)** com `HashMap`, onde P = regras padrao e E = regras especificas.

**`Escada`** — estrutura de avaliacao, construida na carga do cache:

```text
NavigableMap<BigDecimal, Regra> comTeto     # chave = limiteSuperior
Regra semTeto                               # a faixa aberta
```

Resolucao: `comTeto.ceilingEntry(valor)`; se nulo, aplica `semTeto`. **O(log n)**.

**Invariantes verificadas na carga**, por escada:

1. Exatamente uma regra com `limiteSuperior` nulo
2. Limites superiores distintos entre si
3. Todos os limites positivos

Violacao ⇒ registra erro, **preserva o cache anterior**, marca `readiness` degradado. Nunca
serve cache parcial.

### Calculo do score

```text
soma = 0
para cada escada presente no conjunto efetivo:
    regra = escada.resolver(valorTransacao)      # exatamente uma, O(log n)
    soma += regra.acao.aplicar()

para cada regra condicional do conjunto efetivo:
    se regra.avaliar(contexto):                  # Composite E/OU ⇒ um booleano
        soma += regra.acao.aplicar()             # uma vez, nao por condicao

score = max(1, soma)                             # piso uma unica vez, ao final
```

### Schema `motor_decisao`

```sql
CREATE TABLE regra (
    id                UUID          PRIMARY KEY,
    chave             VARCHAR(100),
    tipo_transacao    VARCHAR(50)   NOT NULL,
    descricao         VARCHAR(255)  NOT NULL,
    escada            VARCHAR(100),
    limite_superior   NUMERIC(15,2),
    operador_logico   VARCHAR(3),
    acao_tipo         VARCHAR(10)   NOT NULL,
    acao_pontos       INTEGER       NOT NULL CHECK (acao_pontos > 0),
    ativa             BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_regra_tipo_chave UNIQUE (tipo_transacao, chave),
    CONSTRAINT ck_regra_natureza CHECK (
        (escada IS NOT NULL AND operador_logico IS NULL)
     OR (escada IS NULL     AND limite_superior IS NULL)
    )
);

CREATE TABLE condicao (
    id         UUID         PRIMARY KEY,
    regra_id   UUID         NOT NULL REFERENCES regra(id) ON DELETE CASCADE,
    campo      VARCHAR(50)  NOT NULL,
    operador   VARCHAR(20)  NOT NULL,
    valor      VARCHAR(255) NOT NULL
);

CREATE INDEX idx_regra_tipo_ativa ON regra (tipo_transacao) WHERE ativa;
CREATE INDEX idx_condicao_regra   ON condicao (regra_id);
```

`uk_regra_tipo_chave` implementa FR-022a. Em Postgres, `NULL` nao colide em UNIQUE, portanto
regras sem chave ficam naturalmente fora da restricao — exatamente o comportamento desejado.

### Massa semeada (migration)

Conjunto `PADRAO` — as 7 regras do enunciado:

| chave | escada | limiteSuperior | condicao | acao |
|---|---|---|---|---|
| `faixa_valor_1` | `VALOR_TRANSACAO` | 300.00 | — | SOMAR 200 |
| `faixa_valor_2` | `VALOR_TRANSACAO` | 5000.00 | — | SOMAR 300 |
| `faixa_valor_3` | `VALOR_TRANSACAO` | 20000.00 | — | SOMAR 400 |
| `faixa_valor_4` | `VALOR_TRANSACAO` | `NULL` | — | SOMAR 500 |
| `cpf_lista_permissiva` | — | — | `CPF_EM_LISTA_PERMISSIVA IGUAL true` | SUBTRAIR 200 |
| `cpf_lista_restritiva` | — | — | `CPF_EM_LISTA_RESTRITIVA IGUAL true` | SOMAR 400 |
| `ip_ou_dispositivo_lista_restritiva` | — | — | `IP_EM_LISTA_RESTRITIVA IGUAL true` **OU** `DISPOSITIVO_EM_LISTA_RESTRITIVA IGUAL true` | SOMAR 400 |

Conjuntos especificos do exemplo do enunciado — `PIX` redefine as 7 chaves; `CARTAO` redefine
`faixa_valor_1` (SOMAR 350) e `cpf_lista_permissiva` (SUBTRAIR 400); `TED` redefine
`faixa_valor_1` (SOMAR 280) e `faixa_valor_4` (SOMAR 750).

`CARTAO` e `TED` demonstram heranca: as chaves nao redefinidas continuam valendo com os valores
do `PADRAO`.

---

## api-analise-risco

### Dominio

**`Transacao`** — value object validado na construcao: `cpf` (11 digitos, DV valido, nao
sequencia repetida), `ip`, `idDispositivo` (UUID), `tipoTransacao` (normalizado), `valorTransacao`
(> 0, escala maxima 2).

**`FaixaScore`** — `classificacao` (`BAIXO` \| `MEDIO` \| `ALTO`), `limiteSuperior` (`Integer`,
nulo = sem teto), `decisao` (`APROVADA` \| `NEGADA`).

**`TabelaFaixas`** — `NavigableMap<Integer, FaixaScore>` mais a faixa sem teto. Classificacao por
`ceilingEntry(score)`, **O(log f)**. Mesmas tres invariantes da escada, verificadas na carga.

**`ResultadoListas`** — para cada variavel, os sinais booleanos consumidos pelo motor. O estado
degradado (todos falsos) e representado explicitamente, com um marcador `consultaDegradada` usado
apenas na trilha — nunca na resposta ao cliente.

### Schema `analise_risco`

```sql
CREATE TABLE faixa_score (
    id                UUID         PRIMARY KEY,
    classificacao     VARCHAR(20)  NOT NULL UNIQUE,
    limite_superior   INTEGER,
    decisao           VARCHAR(20)  NOT NULL,
    ordem             INTEGER      NOT NULL UNIQUE,
    atualizado_em     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_faixa_limite CHECK (limite_superior IS NULL OR limite_superior > 0),
    CONSTRAINT ck_faixa_decisao CHECK (decisao IN ('APROVADA','NEGADA'))
);
```

Massa semeada: `BAIXO` ≤ 399 APROVADA · `MEDIO` ≤ 699 APROVADA · `ALTO` sem teto NEGADA.

---

## servico-listas

### Dominio

**`Variavel`** — `tipo` (`CPF` \| `IP` \| `DISPOSITIVO`) e `valor`.

**`Pertinencia`** — `idLista` (UUID), `situacao` (`ATIVA` \| `INATIVA`), `dataInclusao`,
`expiraEm` (opcional). Presenca da pertinencia = consta na lista.

**`EntradaLista`** — uma variavel e suas pertinencias. CPF admite `permissiva` e `restritiva`;
IP e dispositivo admitem apenas `restritiva`.

**Regra de leitura**: uma pertinencia so conta se `situacao = ATIVA` **e** (`expiraEm` nulo ou
futuro). Entrada expirada e tratada como ausente, independentemente do expurgo fisico.

### Tabelas DynamoDB

| Tabela | Partition key | Atributos |
|---|---|---|
| `listas_cpf` | `cpf` (S) | `permissiva` (M), `restritiva` (M) |
| `listas_ip` | `ip` (S) | `restritiva` (M), `expiraEm` (N, TTL) |
| `listas_dispositivo` | `idDispositivo` (S) | `restritiva` (M) |

Mapa de pertinencia: `{ idLista, situacao, dataInclusao }`. Presenca do mapa = consta.

TTL nativo habilitado apenas em `listas_ip`, no atributo `expiraEm` (epoch em segundos).

Consulta das tres variaveis: um `BatchGetItem` sobre as tres tabelas — **1 round-trip, O(1) por
item**.

Sem sort key de proposito: a partition key e a propria variavel, o que distribui
uniformemente. `PK = tipo` com `SK = valor` concentraria todas as consultas de CPF numa
particao — hot partition no dado de maior volume.

---

## servico-auditoria

### Dominio

**`TrilhaDecisao`** — registro imutavel de uma analise concluida.

### Schema `auditoria`

```sql
CREATE TABLE trilha_decisao (
    id                     UUID         PRIMARY KEY,
    id_correlacao          VARCHAR(100) NOT NULL,
    cpf                    VARCHAR(11)  NOT NULL,
    ip                     VARCHAR(45)  NOT NULL,
    id_dispositivo         VARCHAR(36)  NOT NULL,
    tipo_transacao         VARCHAR(50)  NOT NULL,
    valor_transacao        NUMERIC(15,2) NOT NULL,
    score                  INTEGER      NOT NULL,
    classificacao          VARCHAR(20)  NOT NULL,
    decisao                VARCHAR(20)  NOT NULL,
    regras_acionadas       JSONB        NOT NULL,
    resultado_listas       JSONB        NOT NULL,
    consulta_degradada     BOOLEAN      NOT NULL DEFAULT FALSE,
    ocorrido_em            TIMESTAMPTZ  NOT NULL,
    registrado_em          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_trilha_correlacao UNIQUE (id_correlacao)
);

CREATE INDEX idx_trilha_cpf      ON trilha_decisao (cpf);
CREATE INDEX idx_trilha_ocorrido ON trilha_decisao (ocorrido_em DESC);
```

`cpf` em claro por decisao explicita: a trilha e base de investigacao e de defesa em
contestacao. O mascaramento se aplica a log, nao a trilha — publicos e controles de acesso
diferentes.

`uk_trilha_correlacao` da idempotencia ao consumo: reentrega do evento pelo Kafka nao duplica
registro.

---

## Complexidade por operacao

| Operacao | Complexidade | Estrutura |
|---|---|---|
| Consulta das 3 variaveis nas listas | O(1) por item, 1 round-trip | `BatchGetItem` |
| Resolver faixa de valor | O(log n) | `NavigableMap.ceilingEntry` |
| Classificar score em faixa | O(log f) | `NavigableMap.ceilingEntry` |
| Compor conjunto efetivo | O(P + E) | `HashMap` por chave |
| Avaliar regras condicionais | O(R × C) | iteracao com dispatch O(1) |
| Despachar operador | O(1) | `Map<Operador, AvaliadorCondicao>` |
| Extrair valor de campo | O(1) | `Map<Campo, Function<...>>` |
| Ler regras no caminho critico | O(1) | cache em memoria, zero I/O |
| Ler faixas no caminho critico | O(1) | cache em memoria, zero I/O |
| Recarregar cache de regras | O(P + E + n log n) | leitura + construcao das escadas |

R = regras condicionais do conjunto efetivo · C = condicoes por regra · n = faixas por escada ·
f = faixas de score · P e E = regras padrao e especificas
