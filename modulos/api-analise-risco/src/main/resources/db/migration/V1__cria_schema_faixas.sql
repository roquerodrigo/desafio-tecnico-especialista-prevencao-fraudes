CREATE TABLE faixa_score (
    id                UUID         PRIMARY KEY,
    classificacao     VARCHAR(20)  NOT NULL UNIQUE,
    limite_superior   INTEGER,
    decisao           VARCHAR(20)  NOT NULL,
    ordem             INTEGER      NOT NULL UNIQUE,
    atualizado_em     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_faixa_limite_positivo CHECK (limite_superior IS NULL OR limite_superior > 0),
    CONSTRAINT ck_faixa_decisao         CHECK (decisao IN ('APROVADA', 'NEGADA')),
    CONSTRAINT ck_faixa_classificacao   CHECK (classificacao IN ('BAIXO', 'MEDIO', 'ALTO'))
);
