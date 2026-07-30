# ADR 0004 — Invalidação de cache por evento Kafka

**Status**: aceito · **Data**: 2026-07-29

## Contexto

Regras e faixas de score são lidas em **toda** transação e mudam raramente. Mantê-las em memória
elimina I/O do caminho crítico, o que sustenta a meta de latência.

O problema aparece com mais de uma réplica: um `POST /v1/regras` chega em **uma** instância. As
outras seguem servindo o cache antigo, e passam a decidir diferente sobre a mesma transação.

## Decisão

Toda escrita administrativa publica um evento de invalidação; todas as réplicas consomem e
recarregam do Postgres.

Três detalhes fazem isso funcionar:

1. **`group.id` único por instância** (sufixo com hostname). Com group compartilhado, o Kafka
   entrega a mensagem a uma só réplica do grupo — o oposto do necessário. Invalidação exige
   fan-out, não distribuição de carga.
2. **O evento é sinal, não dado.** Não transporta as regras; o consumidor relê da fonte de verdade.
   Isso o mantém pequeno, idempotente e imune a reordenação — duas recargas seguidas convergem.
3. **Quem atende recarrega seu próprio cache de forma síncrona**, sem esperar o evento voltar. Se o
   broker estiver fora, a instância que atendeu já está correta e o erro fica registrado.

Contraste deliberado: o consumidor da **trilha de auditoria** usa `group.id` **compartilhado**, porque
ali o objetivo é distribuir o processamento — cada decisão deve ser persistida uma vez. Fan-out
produziria registros duplicados.

## Consequências

- Alteração propaga em segundos, sem restart. Verificado em teste de integração e no roteiro do
  quickstart.
- Falha de publicação **não** propaga: a escrita já foi persistida, e derrubar a resposta faria o
  cliente reenviar e colidir em chave duplicada. O custo é réplicas possivelmente desatualizadas
  até a próxima alteração, com erro em log.
- Carga inválida preserva o cache anterior. Nunca se serve cache vazio: sem regras, o motor
  produziria score arbitrário que a orquestração trataria como legítimo.

## Alternativas consideradas

**TTL curto** — sem broker, mais simples. Rejeitado por criar janela em que réplicas divergem: duas
instâncias classificando o mesmo score de formas diferentes é inaceitável em antifraude.

**Ler do banco a cada transação** — sempre consistente e trivial. Rejeitado por colocar I/O no
caminho crítico de cada transação, contra o RNF de latência que o enunciado prioriza.
