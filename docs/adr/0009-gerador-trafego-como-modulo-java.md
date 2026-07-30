# ADR 0009 — Gerador de tráfego como módulo Java, não ferramenta externa

**Status**: aceito · **Data**: 2026-07-30

## Contexto

O critério de sucesso SC-001 estabelece que a decisão de risco deve ser obtida em menos de 150 ms
no percentil 95. Até aqui, essa meta era **declarada mas nunca medida** — o sistema respondia
rápido em chamadas `curl` isoladas, o que não diz nada sobre o comportamento sob concorrência.

Também faltava uma forma de demonstrar o sistema em movimento: disparar requisições uma a uma não
mostra caches sendo usados, réplicas atendendo em paralelo, nem o pipeline de auditoria sob volume.

## Decisão

Um quinto módulo Java no monorepo, `gerador-trafego`, com endpoints de controle. Sobe com o
compose mas fica **ocioso** até receber `POST /v1/cargas`.

## Racional

**Por que não k6, Gatling ou JMeter.** São ferramentas melhores para carga — calculam percentis
corretamente, oferecem ramp-up, relatórios prontos. Mas o enunciado exclui explicitamente
"collections de ferramentas como Postman, Insomnia ou similares" da avaliação, e um script de k6 cai
na mesma categoria: é configuração de ferramenta externa, não código do candidato.

Um módulo Java é **código avaliável**, sujeito aos mesmos padrões do resto: Clean Architecture,
Ubiquitous Language em português, e o gate de cobertura de 90%.

**Por que ocioso por padrão.** Gerar tráfego automaticamente na subida consumiria recursos sem
ninguém pedir e encheria a trilha de auditoria de registros sintéticos antes de qualquer
demonstração. A carga é uma decisão explícita de quem apresenta.

**Por que uma carga por vez.** Duas execuções simultâneas misturariam amostras de latência, e o
percentil resultante não descreveria nenhuma das duas. A segunda tentativa recebe `409`.

## Decisões técnicas internas

**Virtual threads.** Carga é limitada por I/O; uma thread virtual por requisição sustenta centenas
de chamadas em voo sem dimensionar pool à mão. Com threads de plataforma, o pool viraria o gargalo e
o gerador estaria medindo a si mesmo, não o sistema.

**Percentil por nearest-rank, declarado.** Percentil é ambíguo — há várias definições, e bibliotecas
diferentes devolvem valores distintos para a mesma amostra. O método está documentado e testado
contra valores conhecidos: posição `ceil(p/100 × n)`, sem interpolação, de modo que o valor
reportado é sempre uma medição que de fato aconteceu.

**CPFs com dígito verificador válido.** Não é detalhe de conveniência: a API valida o DV, então um
gerador ingênuo receberia `400` em 100% das requisições e mediria a latência da validação de
entrada. O tempo seria real e a conclusão, falsa.

**Tráfego calibrado, não uniforme.** As proporções (60/25/15 entre tipos, quatro faixas de valor,
20% de CPFs em listas) existem para que a composição de regras seja efetivamente exercitada. Tráfego
uniforme cairia quase sempre na mesma faixa e mostraria 100% de aprovação — mediria um único caminho
de código. O relatório inclui a distribuição de decisões justamente para tornar isso verificável.

## Consequências

- SC-001 passa a ser verificável com um comando, e o campo `metaAtingida` responde objetivamente.
- Quinto deployable e mais código sob o gate de cobertura — que ele cumpre, sem exclusões.
- O gerador mede latência **do ponto de vista do cliente**, incluindo rede e serialização, que é
  exatamente o que SC-001 especifica.
- Erros são contabilizados como dado da medição, não como falha da execução. Derrubar um serviço
  durante a carga produz um relatório com a proporção de `HTTP_503` — útil, e não um teste abortado.

## Alternativa considerada e rejeitada

**Apenas um teste JUnit de latência** (`LatenciaAnaliseIT`). Fecharia SC-001 dentro da suíte
existente, sem novo deployable. Rejeitado por não servir para demonstração ao vivo nem para carga
sustentada: seria uma medição pontual em ambiente de teste, não tráfego contra o sistema real
rodando no compose.
