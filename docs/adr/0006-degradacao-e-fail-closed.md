# ADR 0006 — Degradação nas listas, fail-closed no motor

**Status**: aceito · **Data**: 2026-07-29

## Contexto

O RNF de confiabilidade pede consistência "mesmo em cenários de falhas parciais". A orquestração
depende de dois serviços, e a pergunta — o que fazer quando um deles cai — tem respostas diferentes
para cada um.

## Decisão

**Serviço de listas indisponível → degrada.** A análise prossegue tratando todas as variáveis como
ausentes de qualquer lista. A transação continua sendo avaliada pelas regras de valor.

**Motor de decisão indisponível → fail-closed.** Sem score não existe decisão. Responde
`503` com `Retry-After`, nunca `200` com `NEGADA`.

## Racional

A distinção é de negócio, não de infraestrutura: **sinal de lista é desejável, score é essencial.**

Se a queda das listas derrubasse a análise, 100% das transações deixariam de ser aprovadas por causa
de um fator que é apenas um dos componentes do risco. Inaceitável em produção.

Já o `503` em vez de `NEGADA` importa porque `NEGADA` precisa significar **decisão real de risco**.
Se indisponibilidade técnica virasse negativa:

- o cliente não distinguiria "cliente de risco" de "nosso serviço caiu";
- um retry legítimo ficaria indistinguível de insistência em transação negada;
- métricas de negação de risco ficariam contaminadas por incidentes de infraestrutura.

## Consequências

- A porta de listas tem contrato **sem exceção**: a implementação trata a falha e devolve o estado
  degradado. A política pertence à fronteira, não a quem orquestra.
- A porta de score **propaga**. As duas assinaturas codificam a decisão.
- A degradação é registrada na trilha (`consultaListasDegradada`), nunca silenciosa. Sem isso não
  haveria como medir quanto risco a indisponibilidade gerou.
- O `detail` do `503` é genérico de propósito: não revela qual dependência falhou, nem expõe score.
- Todo client HTTP declara timeout de conexão e de leitura. Sem timeout, uma dependência lenta
  prende threads e transforma latência do vizinho em indisponibilidade própria.
