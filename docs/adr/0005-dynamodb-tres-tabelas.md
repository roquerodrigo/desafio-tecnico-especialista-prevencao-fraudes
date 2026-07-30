# ADR 0005 — DynamoDB com três tabelas, não single-table design

**Status**: aceito · **Data**: 2026-07-29

## Contexto

As listas restritivas e permissivas têm perfil de dado oposto ao das regras: podem chegar a milhões
de entradas, e o acesso é lookup pontual por chave. Regras são dezenas, lidas por inteiro a cada
transação.

A ortodoxia do DynamoDB, difundida pela própria AWS, é **single-table design**: colocar todas as
entidades numa tabela só, distinguidas por prefixo na partition key.

## Decisão

Três tabelas: `listas_cpf`, `listas_ip`, `listas_dispositivo`. A partition key é a própria variável.

## Consequências

**O custo que se imaginaria não existe.** `BatchGetItem` aceita múltiplas tabelas no mesmo
`RequestItems`, então consultar as três variáveis continua sendo **um round-trip**. Era a única
objeção séria, e ela não se aplica.

Ganhos concretos:

- **Schemas genuinamente diferentes.** CPF tem duas pertinências; IP e dispositivo têm uma. Em
  tabela única, isso seria atributo opcional vazio em 2/3 dos itens.
- **TTL apenas onde faz sentido.** IP é identificador efêmero — CGNAT e DHCP reatribuem endereços,
  e bloqueio perpétuo puniria quem herdar o endereço. CPF e dispositivo não expiram. O TTL do
  DynamoDB é por tabela, então em single-table isso seria impossível.
- **Partition key limpa**, sem prefixo sintético. O prefixo só existiria para desambiguar dentro de
  uma tabela compartilhada.
- **Métricas independentes.** Throttling na tabela de CPF não se confunde com a de dispositivo.

## Por que não single-table

Single-table design resolve um problema específico: colocar entidades **relacionadas** na mesma
partição para eliminar join, quando os padrões de acesso são variados. Aqui não há relacionamento
algum — são três domínios de lookup pontual independentes. Aplicar o padrão seria seguir a forma
sem ter o problema que ela resolve.

## Nota sobre a partition key

A chave é a variável completa (o CPF em si), não o tipo. `PK = "CPF"` com sort key no valor
concentraria **todas** as consultas de CPF numa única partição — hot partition clássico, exatamente
no dado de maior volume.

## Nota sobre TTL

O TTL é declarado, mas a expiração é **também** verificada na leitura. A AWS documenta que o
expurgo ocorre "within a few days" da expiração e recomenda filtrar itens expirados. Portanto o
filtro é obrigatório em produção, não um contorno do emulador — e de quebra torna irrelevante o
fato de o DynamoDB Local não expurgar.
