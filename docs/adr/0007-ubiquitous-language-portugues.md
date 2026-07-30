# ADR 0007 — Ubiquitous Language em português

**Status**: aceito · **Data**: 2026-07-29

## Contexto

O domínio é prevenção a fraudes num banco brasileiro. Os especialistas do negócio falam de "lista
restritiva", "faixa de valor", "score de risco", "transação negada".

O enunciado é ambíguo quanto ao idioma: os campos de entrada estão em inglês (`device_id`,
`tx_type`), mas o exemplo de regras em JSON está em português (`chave`, `condicoes`, `acao`,
`SOMAR`). Também alterna `op_type` e `tx_type` para o mesmo conceito.

## Decisão

Domínio, contratos e rotas em **português**. Sufixo de pattern em inglês.

| Elemento | Exemplo |
|---|---|
| Classes de domínio | `MotorDecisao`, `AvaliadorRisco`, `TabelaFaixas` |
| Com sufixo de pattern | `RegraRepository`, `CondicaoStrategy`, `RegraFactory` |
| Campos e métodos | `valorTransacao`, `calcularScore()` |
| Rotas | `/v1/analises-risco`, `/v1/faixas-score` |
| Termos consolidados | `score`, `id`, `ip`, `cpf`, `PIX` |

**Identificadores sem acento**: `decisao`, `situacao`, `MEDIO`. Java aceita acentos, mas isso quebra
em encoding e teclado. A acentuação correta fica onde é lida por humano: mensagens de erro,
descrições OpenAPI, README, estes ADRs.

**Case por camada**: `camelCase` em JSON e Java, `snake_case` em Postgres, `camelCase` em DynamoDB,
`kebab-case` nas rotas. Não é inconsistência — é a convenção idiomática de cada tecnologia. Coluna
Postgres em camelCase exigiria aspas em toda query.

## Racional

Esta é a definição de Ubiquitous Language do DDD: uma linguagem só, compartilhada entre código e
negócio. Um glossário que traduz "transaction" para "transação" a cada fronteira é atrito puro, e
cria a chance de o código e a conversa divergirem.

Manter o sufixo de pattern em inglês é escolha consciente: `Repository`, `Strategy` e `Factory` são
nomes próprios reconhecíveis por qualquer desenvolvedor. Traduzi-los (`RepositorioRegra`) tornaria
o padrão menos legível, não mais.

## Consequências

- O código é lido diretamente por quem conhece o negócio.
- Divergência do exemplo do enunciado nos nomes dos campos, documentada como premissa no README.
- Uma mistura visível: `@JsonRawValue` e outras anotações vêm em inglês, por serem da biblioteca.
  Isso é fronteira, não domínio.
