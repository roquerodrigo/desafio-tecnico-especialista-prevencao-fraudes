-- Massa inicial. Semeia DADO, nao codigo: cada linha aqui e editavel pelo CRUD em runtime, e o
-- conjunto PADRAO e um tipo de transacao como qualquer outro.
--
-- Conjunto PADRAO — as 7 regras do enunciado.

INSERT INTO regra (id, chave, tipo_transacao, descricao, escada, limite_superior, acao_tipo, acao_pontos) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'faixa_valor_1', 'PADRAO', 'Valor de R$0,01 a R$300,00',        'VALOR_TRANSACAO',   300.00, 'SOMAR', 200),
  ('a0000000-0000-4000-8000-000000000002', 'faixa_valor_2', 'PADRAO', 'Valor de R$300,01 a R$5.000,00',    'VALOR_TRANSACAO',  5000.00, 'SOMAR', 300),
  ('a0000000-0000-4000-8000-000000000003', 'faixa_valor_3', 'PADRAO', 'Valor de R$5.000,01 a R$20.000,00', 'VALOR_TRANSACAO', 20000.00, 'SOMAR', 400),
  ('a0000000-0000-4000-8000-000000000004', 'faixa_valor_4', 'PADRAO', 'Valor acima de R$20.000,00',        'VALOR_TRANSACAO',     NULL, 'SOMAR', 500);

INSERT INTO regra (id, chave, tipo_transacao, descricao, operador_logico, acao_tipo, acao_pontos) VALUES
  ('a0000000-0000-4000-8000-000000000005', 'cpf_lista_permissiva',          'PADRAO', 'CPF em lista permissiva',                NULL, 'SUBTRAIR', 200),
  ('a0000000-0000-4000-8000-000000000006', 'cpf_lista_restritiva',          'PADRAO', 'CPF em lista restritiva',                NULL, 'SOMAR',    400),
  ('a0000000-0000-4000-8000-000000000007', 'ip_ou_dispositivo_lista_restritiva', 'PADRAO', 'IP ou dispositivo em lista restritiva', 'OU', 'SOMAR', 400);

INSERT INTO condicao (id, regra_id, campo, operador, valor) VALUES
  ('c0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000005', 'CPF_EM_LISTA_PERMISSIVA',         'IGUAL', 'true'),
  ('c0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000006', 'CPF_EM_LISTA_RESTRITIVA',         'IGUAL', 'true'),
  ('c0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000007', 'IP_EM_LISTA_RESTRITIVA',          'IGUAL', 'true'),
  ('c0000000-0000-4000-8000-000000000008', 'a0000000-0000-4000-8000-000000000007', 'DISPOSITIVO_EM_LISTA_RESTRITIVA', 'IGUAL', 'true');

-- Conjunto PIX — redefine todas as chaves do padrao.

INSERT INTO regra (id, chave, tipo_transacao, descricao, escada, limite_superior, acao_tipo, acao_pontos) VALUES
  ('b0000000-0000-4000-8000-000000000001', 'faixa_valor_1', 'PIX', 'PIX de R$0,01 a R$300,00',        'VALOR_TRANSACAO',   300.00, 'SOMAR', 300),
  ('b0000000-0000-4000-8000-000000000002', 'faixa_valor_2', 'PIX', 'PIX de R$300,01 a R$5.000,00',    'VALOR_TRANSACAO',  5000.00, 'SOMAR', 400),
  ('b0000000-0000-4000-8000-000000000003', 'faixa_valor_3', 'PIX', 'PIX de R$5.000,01 a R$20.000,00', 'VALOR_TRANSACAO', 20000.00, 'SOMAR', 430),
  ('b0000000-0000-4000-8000-000000000004', 'faixa_valor_4', 'PIX', 'PIX acima de R$20.000,00',        'VALOR_TRANSACAO',     NULL, 'SOMAR', 700);

INSERT INTO regra (id, chave, tipo_transacao, descricao, operador_logico, acao_tipo, acao_pontos) VALUES
  ('b0000000-0000-4000-8000-000000000005', 'cpf_lista_permissiva',               'PIX', 'PIX: CPF em lista permissiva',                NULL, 'SUBTRAIR', 100),
  ('b0000000-0000-4000-8000-000000000006', 'cpf_lista_restritiva',               'PIX', 'PIX: CPF em lista restritiva',                NULL, 'SOMAR',    200),
  ('b0000000-0000-4000-8000-000000000007', 'ip_ou_dispositivo_lista_restritiva', 'PIX', 'PIX: IP ou dispositivo em lista restritiva',  'OU', 'SOMAR',    400);

INSERT INTO condicao (id, regra_id, campo, operador, valor) VALUES
  ('c1000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000005', 'CPF_EM_LISTA_PERMISSIVA',         'IGUAL', 'true'),
  ('c1000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000006', 'CPF_EM_LISTA_RESTRITIVA',         'IGUAL', 'true'),
  ('c1000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000007', 'IP_EM_LISTA_RESTRITIVA',          'IGUAL', 'true'),
  ('c1000000-0000-4000-8000-000000000008', 'b0000000-0000-4000-8000-000000000007', 'DISPOSITIVO_EM_LISTA_RESTRITIVA', 'IGUAL', 'true');

-- Conjunto CARTAO — redefine apenas 2 chaves. As faixas 2, 3 e 4 e as regras de lista restritiva
-- sao herdadas do PADRAO pela chave de sobreposicao.

INSERT INTO regra (id, chave, tipo_transacao, descricao, escada, limite_superior, acao_tipo, acao_pontos) VALUES
  ('d0000000-0000-4000-8000-000000000001', 'faixa_valor_1', 'CARTAO', 'CARTAO de R$0,01 a R$300,00', 'VALOR_TRANSACAO', 300.00, 'SOMAR', 350);

INSERT INTO regra (id, chave, tipo_transacao, descricao, operador_logico, acao_tipo, acao_pontos) VALUES
  ('d0000000-0000-4000-8000-000000000002', 'cpf_lista_permissiva', 'CARTAO', 'CARTAO: CPF em lista permissiva', NULL, 'SUBTRAIR', 400);

INSERT INTO condicao (id, regra_id, campo, operador, valor) VALUES
  ('c2000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000002', 'CPF_EM_LISTA_PERMISSIVA', 'IGUAL', 'true');

-- Conjunto TED — redefine a primeira e a ultima faixa. As faixas 2 e 3 e todas as regras de
-- lista sao herdadas do PADRAO.

INSERT INTO regra (id, chave, tipo_transacao, descricao, escada, limite_superior, acao_tipo, acao_pontos) VALUES
  ('e0000000-0000-4000-8000-000000000001', 'faixa_valor_1', 'TED', 'TED de R$0,01 a R$300,00', 'VALOR_TRANSACAO', 300.00, 'SOMAR', 280),
  ('e0000000-0000-4000-8000-000000000004', 'faixa_valor_4', 'TED', 'TED acima de R$20.000,00', 'VALOR_TRANSACAO',   NULL, 'SOMAR', 750);
