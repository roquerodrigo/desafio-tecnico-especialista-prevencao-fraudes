CREATE TABLE regra (
    id                UUID          PRIMARY KEY,
    chave             VARCHAR(100),
    tipo_transacao    VARCHAR(50)   NOT NULL,
    descricao         VARCHAR(255)  NOT NULL,
    escada            VARCHAR(100),
    limite_superior   NUMERIC(15,2),
    operador_logico   VARCHAR(3),
    acao_tipo         VARCHAR(10)   NOT NULL,
    acao_pontos       INTEGER       NOT NULL,
    ativa             BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_regra_pontos_positivos CHECK (acao_pontos > 0),
    CONSTRAINT ck_regra_acao_tipo        CHECK (acao_tipo IN ('SOMAR', 'SUBTRAIR')),
    CONSTRAINT ck_regra_operador_logico  CHECK (operador_logico IS NULL OR operador_logico IN ('E', 'OU')),
    CONSTRAINT ck_regra_limite_positivo  CHECK (limite_superior IS NULL OR limite_superior > 0),

    -- Uma regra e escada ou condicional, nunca as duas. Regra de escada nao declara operador
    -- logico; regra condicional nao declara limite superior.
    CONSTRAINT ck_regra_natureza CHECK (
        (escada IS NOT NULL AND operador_logico IS NULL)
     OR (escada IS NULL     AND limite_superior IS NULL)
    )
);

-- Implementa a unicidade da chave de sobreposicao por conjunto. Em Postgres, NULL nao colide em
-- UNIQUE, portanto regras sem chave ficam naturalmente fora da restricao — que e o comportamento
-- desejado: regra sem chave e sempre aditiva e pode repetir.
ALTER TABLE regra ADD CONSTRAINT uk_regra_tipo_chave UNIQUE (tipo_transacao, chave);

CREATE TABLE condicao (
    id         UUID         PRIMARY KEY,
    regra_id   UUID         NOT NULL REFERENCES regra (id) ON DELETE CASCADE,
    campo      VARCHAR(50)  NOT NULL,
    operador   VARCHAR(20)  NOT NULL,
    valor      VARCHAR(255) NOT NULL
);

CREATE INDEX idx_regra_tipo_ativa ON regra (tipo_transacao) WHERE ativa;
CREATE INDEX idx_condicao_regra   ON condicao (regra_id);
