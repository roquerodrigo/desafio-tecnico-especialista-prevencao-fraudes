CREATE TABLE trilha_decisao (
    id                     UUID          PRIMARY KEY,
    id_correlacao          VARCHAR(100)  NOT NULL,
    cpf                    VARCHAR(11)   NOT NULL,
    ip                     VARCHAR(45)   NOT NULL,
    id_dispositivo         VARCHAR(36)   NOT NULL,
    tipo_transacao         VARCHAR(50)   NOT NULL,
    valor_transacao        NUMERIC(15,2) NOT NULL,
    score                  INTEGER       NOT NULL,
    classificacao          VARCHAR(20)   NOT NULL,
    decisao                VARCHAR(20)   NOT NULL,
    regras_acionadas       JSONB         NOT NULL,
    resultado_listas       JSONB         NOT NULL,
    consulta_degradada     BOOLEAN       NOT NULL DEFAULT FALSE,
    ocorrido_em            TIMESTAMPTZ   NOT NULL,
    registrado_em          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- Da idempotencia ao consumo: reentrega do evento pelo Kafka nao duplica registro.
    CONSTRAINT uk_trilha_correlacao UNIQUE (id_correlacao),
    CONSTRAINT ck_trilha_score      CHECK (score >= 1),
    CONSTRAINT ck_trilha_decisao    CHECK (decisao IN ('APROVADA', 'NEGADA'))
);

-- cpf indexado e em claro: a trilha e base de investigacao de fraude e de defesa em contestacao,
-- com acesso controlado. O mascaramento aplica-se a log, que tem publico e retencao diferentes.
CREATE INDEX idx_trilha_cpf      ON trilha_decisao (cpf);
CREATE INDEX idx_trilha_ocorrido ON trilha_decisao (ocorrido_em DESC);
