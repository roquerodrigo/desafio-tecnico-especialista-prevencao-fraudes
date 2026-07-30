# ADR 0002 — Faixas declaradas apenas por limite superior

**Status**: aceito · **Data**: 2026-07-29

## Contexto

O enunciado descreve as faixas de valor com limite inferior e superior: `0,01`–`300,00`,
`300,01`–`5.000,00`, e assim por diante. Também exige que regras específicas por tipo de transação
possam **redefinir** uma faixa, compondo-se com o conjunto padrão pela chave de sobreposição.

Essas duas exigências juntas criam um problema. Se `CARTAO` redefinir `faixa_valor_1` como
`0,01`–`500,00`, essa faixa passa a **sobrepor** a `faixa_valor_2` herdada do padrão
(`300,01`–`5.000,00`). Uma transação de R$400 se enquadraria em ambas e pontuaria duas vezes. O
enunciado não proíbe isso — e é justamente o tipo de erro de configuração que não dá sintoma até
alguém investigar por que um score está alto.

## Decisão

Cada faixa declara **apenas o limite superior**. O limite inferior é o teto da faixa anterior.
`limiteSuperior` nulo significa "sem teto".

A resolução usa `NavigableMap.ceilingEntry`, em O(log n).

## Consequências

- Lacuna e sobreposição deixam de ser **expressáveis**, qualquer que seja a composição. Não é uma
  validação que pode ser esquecida: a estrutura não admite o estado inválido.
- Redefinir uma faixa com outro teto é sempre seguro. `CARTAO` declara `≤500` e a escada vira
  `500 → 5.000 → 20.000 → ∞`: contígua e exclusiva.
- Duas invariantes precisam ser verificadas na carga: exatamente uma faixa sem teto, e limites
  distintos. Violação preserva o cache anterior.
- O contrato divergiu do exemplo do enunciado, que declara `valorMinimo`. Está documentado como
  premissa no README.

## Nota sobre o vão do enunciado

As faixas do enunciado deixam descoberto o intervalo entre `300,00` e `300,01`. Esse **não** é o
argumento a favor desta decisão: como a entrada é limitada a duas casas decimais (ADR implícito na
premissa de precisão monetária), tal valor é inalcançável. O ganho real é o descrito acima —
composição sempre válida.
