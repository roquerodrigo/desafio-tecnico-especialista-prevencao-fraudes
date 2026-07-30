-- Politica inicial de risco. O enunciado a descreve como valida "inicialmente", o que sinaliza
-- que muda — por isso a decisao de cada faixa e dado, alteravel por PUT /v1/faixas-score.
--
-- Apenas o teto e declarado: o piso de cada faixa e o teto da anterior. Assim nenhum score
-- possivel fica sem faixa e nenhum score cai em duas faixas.

INSERT INTO faixa_score (id, classificacao, limite_superior, decisao, ordem) VALUES
  ('f0000000-0000-4000-8000-000000000001', 'BAIXO',  399,  'APROVADA', 1),
  ('f0000000-0000-4000-8000-000000000002', 'MEDIO',  699,  'APROVADA', 2),
  ('f0000000-0000-4000-8000-000000000003', 'ALTO',   NULL, 'NEGADA',   3);
