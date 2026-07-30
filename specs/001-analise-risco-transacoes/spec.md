# Feature Specification: Plataforma de Analise de Risco de Transacoes

**Feature Branch**: `001-analise-risco-transacoes`

**Created**: 2026-07-29

**Status**: Draft

**Input**: Desafio tecnico do Banco ACME — servico de avaliacao de risco de transacoes
financeiras exposto por HTTP REST, composto por orquestracao da analise, listas restritivas
e permissivas, e motor de decisao com regras dinamicas. Enunciado completo em
`docs/desafio.md`.

## Clarifications

### Session 2026-07-29

Decisoes de arquitetura e contrato foram fechadas com o stakeholder antes desta
especificacao e estao registradas no dossie de decisoes. As cinco ambiguidades abaixo eram
residuais — todas de modelagem de dominio — e foram resolvidas por julgamento tecnico
autorizado pelo stakeholder.

- Q: Qual a precisao aceita para o valor da transacao? → A: Decimal com no maximo 2 casas
  (centavo e a menor unidade em BRL); mais casas sao rejeitadas como invalidas. Comparacoes
  usam decimal exato, nunca ponto flutuante.
- Q: O tipo de transacao e uma enumeracao fechada ou texto livre? → A: Texto livre
  normalizado (maiusculas, nao vazio, limite de tamanho). Enumeracao fechada exigiria novo
  deploy para cada tipo novo, violando o principio de configuracao como dado.
- Q: A chave de sobreposicao pode repetir dentro do mesmo conjunto de regras? → A: Nao. A
  chave e unica por par (tipo de transacao, chave). Regras sem chave nao participam da
  restricao e podem coexistir livremente.
- Q: O modelo suporta mais de uma escada de faixas? → A: Sim. Escadas sao identificadas por
  nome e sao independentes; as invariantes de integridade valem por escada, nao globalmente.
- Q: Qual o formato aceito do CPF na entrada? → A: Apenas digitos, 11 posicoes, sem mascara.
  Digitos verificadores validados e sequencias de digito repetido rejeitadas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Obter decisao de risco para uma transacao (Priority: P1)

Uma aplicacao cliente do banco precisa saber se pode prosseguir com uma transacao
financeira. Ela envia os dados da transacao — CPF do cliente, endereco IP, identificador do
dispositivo, tipo e valor — e recebe de volta uma unica informacao: aprovar ou negar.

O cliente nao precisa conhecer criterios de risco, pontuacoes ou regras. Ele faz uma
pergunta de negocio e recebe uma resposta de negocio.

**Why this priority**: e a razao de existir da plataforma. Sem esta jornada, nenhuma outra
entrega valor — as regras nao seriam consultadas, as listas nao seriam verificadas e nao
haveria decisao a auditar.

**Independent Test**: com regras e listas previamente carregadas, enviar uma transacao e
verificar que a resposta contem exatamente uma decisao (`APROVADA` ou `NEGADA`) coerente com
as regras cadastradas, sem expor pontuacao ou classificacao.

**Acceptance Scenarios**:

1. **Given** regras cadastradas e um CPF que nao consta em nenhuma lista, **When** o cliente
   solicita analise de uma transacao de valor baixo, **Then** o sistema responde `APROVADA`
   e a resposta nao contem pontuacao nem classificacao de risco.
2. **Given** um CPF presente em lista restritiva e regras que penalizam essa condicao,
   **When** o cliente solicita analise de uma transacao de valor alto, **Then** o sistema
   responde `NEGADA`.
3. **Given** um CPF presente em lista permissiva e regras que bonificam essa condicao,
   **When** o cliente solicita analise de uma transacao que sem a bonificacao seria negada,
   **Then** o sistema responde `APROVADA`.
4. **Given** uma transacao com CPF invalido, **When** o cliente solicita analise, **Then** o
   sistema rejeita a solicitacao informando o campo invalido, sem consultar listas nem
   calcular pontuacao.
5. **Given** um identificador de dispositivo presente em lista restritiva, **When** o
   cliente solicita analise, **Then** a penalizacao correspondente e aplicada uma unica vez,
   ainda que o IP tambem esteja em lista restritiva.

---

### User Story 2 - Gerenciar regras de decisao sem novo deploy (Priority: P1)

Um analista de prevencao a fraudes precisa ajustar como o risco e calculado: criar uma regra
nova, alterar a pontuacao de uma existente, remover uma que deixou de fazer sentido ou
consultar o conjunto vigente.

Nada disso pode exigir intervencao de engenharia, nova versao da aplicacao ou reinicio de
servico. A mudanca precisa valer para as proximas transacoes.

**Why this priority**: e requisito explicito do enunciado que usuarios de negocio gerenciem
as regras, e a razao pela qual as regras sao dado e nao codigo. Sem isso, a plataforma seria
uma calculadora fixa.

**Independent Test**: cadastrar uma regra, submeter uma transacao e observar seu efeito na
decisao; alterar a pontuacao da mesma regra, repetir a transacao e observar a decisao mudar
— tudo sem reiniciar nada.

**Acceptance Scenarios**:

1. **Given** o conjunto de regras vigente, **When** o analista cadastra uma regra nova para
   um tipo de transacao, **Then** a proxima analise daquele tipo ja considera a regra.
2. **Given** uma regra existente, **When** o analista altera sua pontuacao, **Then** a
   proxima analise usa a nova pontuacao sem que nenhum servico seja reiniciado.
3. **Given** uma regra existente, **When** o analista a remove, **Then** a proxima analise
   nao a considera mais.
4. **Given** o conjunto de regras vigente, **When** o analista consulta as regras de um tipo
   de transacao, **Then** recebe as regras efetivamente aplicadas a esse tipo, resultado da
   composicao entre o conjunto padrao e o especifico.
5. **Given** uma tentativa de cadastro com campo obrigatorio ausente ou valor de enumeracao
   desconhecido, **When** o analista submete, **Then** o sistema rejeita informando o
   problema e o conjunto vigente permanece intacto.

---

### User Story 3 - Compor regras padrao com regras por tipo de transacao (Priority: P1)

Um analista mantem um conjunto de regras padrao valido para qualquer transacao e, sobre ele,
ajustes especificos por tipo. Para PIX, todas as pontuacoes podem ser diferentes; para
cartao, apenas duas mudam e o restante segue o padrao.

Cada regra declara uma chave que identifica o predicado avaliado. A chave e o que permite
compor os dois conjuntos sem duplicar tudo.

**Why this priority**: e o requisito mais elaborado do enunciado e o que define se a
plataforma escala em numero de tipos de transacao sem multiplicar o cadastro.

**Independent Test**: cadastrar um conjunto padrao e um conjunto especifico que redefine
apenas uma chave; verificar que as regras nao redefinidas continuam valendo com os valores
do padrao e que a redefinida vale com o valor especifico.

**Acceptance Scenarios**:

1. **Given** conjunto padrao com uma regra de chave K, **When** existe regra especifica do
   tipo com a mesma chave K, **Then** a especifica substitui a padrao para aquele tipo.
2. **Given** conjunto padrao com regras de chaves K1 e K2, **When** o conjunto especifico
   redefine apenas K1, **Then** K2 permanece ativa com a pontuacao do padrao.
3. **Given** conjunto padrao, **When** o conjunto especifico traz uma chave inexistente no
   padrao, **Then** essa regra e acrescentada ao conjunto efetivo.
4. **Given** uma regra sem chave declarada, **When** o conjunto efetivo e composto, **Then**
   essa regra e sempre acrescentada e nunca substitui outra.
5. **Given** um tipo de transacao sem nenhuma regra especifica, **When** uma transacao desse
   tipo e analisada, **Then** apenas o conjunto padrao e aplicado.

---

### User Story 4 - Consultar presenca em listas restritivas e permissivas (Priority: P2)

O processo de analise precisa saber se o CPF, o IP ou o dispositivo da transacao constam em
listas de bloqueio ou de liberacao. Um mesmo CPF pode constar em ambas; IP e dispositivo
constam apenas em listas restritivas; e e possivel que nada conste em lista alguma.

**Why this priority**: o sinal de lista e um dos fatores que compoem o risco, mas a analise
permanece possivel sem ele (as regras de valor continuam pontuando). E importante, nao
essencial.

**Independent Test**: carregar entradas conhecidas nas listas e consultar cada variavel,
verificando que a resposta distingue corretamente presenca em permissiva, restritiva, em
ambas e em nenhuma.

**Acceptance Scenarios**:

1. **Given** um CPF cadastrado em lista permissiva e em lista restritiva, **When** e
   consultado, **Then** a resposta indica presenca em ambas.
2. **Given** um CPF nao cadastrado em lista alguma, **When** e consultado, **Then** a
   resposta indica ausencia em todas.
3. **Given** um IP cadastrado em lista restritiva, **When** e consultado, **Then** a resposta
   indica presenca em restritiva.
4. **Given** uma entrada de lista de IP cuja validade expirou, **When** o IP e consultado,
   **Then** a resposta indica ausencia, como se a entrada nao existisse.
5. **Given** as tres variaveis de uma transacao, **When** sao consultadas em conjunto,
   **Then** o resultado de todas retorna em uma unica resposta.

---

### User Story 5 - Manter as listas atualizadas (Priority: P2)

Um operador precisa incluir e atualizar entradas nas listas — um CPF que passou a ser
suspeito, um dispositivo comprometido, um IP identificado em ataque. A atualizacao precisa
passar a valer imediatamente para todas as consultas subsequentes, sem reinicio.

**Why this priority**: sem carga, as listas ficam vazias e a User Story 4 nao tem o que
responder. Nao exige CRUD completo — o enunciado dispensa —, apenas ingestao confiavel.

**Independent Test**: consultar uma variavel e obter ausencia; carregar a variavel na lista;
consultar novamente e obter presenca, sem qualquer reinicio.

**Acceptance Scenarios**:

1. **Given** um CPF ausente das listas, **When** o operador o carrega em lista restritiva,
   **Then** a consulta seguinte indica presenca em restritiva.
2. **Given** uma entrada existente, **When** o operador altera sua situacao, **Then** a
   consulta seguinte reflete a alteracao.
3. **Given** uma tentativa de carga sem credencial administrativa, **When** submetida,
   **Then** o sistema rejeita e as listas permanecem inalteradas.
4. **Given** uma entrada de IP carregada com prazo de validade, **When** o prazo e informado,
   **Then** a entrada deixa de valer apos o prazo sem intervencao manual.

---

### User Story 6 - Configurar faixas de score e politica de decisao em runtime (Priority: P2)

Um gestor de risco precisa ajustar os limites que separam risco baixo, medio e alto — e
tambem se cada faixa aprova ou nega. A politica de risco muda com o cenario de fraude e nao
pode depender de nova versao da aplicacao.

**Why this priority**: e requisito explicito de configuracao externa ao codigo, e o
enunciado descreve a politica de aprovacao como valida "inicialmente", sinalizando que muda.

**Independent Test**: submeter uma transacao que resulte em risco medio e observar aprovacao;
alterar a faixa de risco medio para negar; repetir a mesma transacao e observar negativa.

**Acceptance Scenarios**:

1. **Given** as faixas vigentes, **When** o gestor consulta a configuracao, **Then** recebe
   as faixas com seus limites e a decisao associada a cada uma.
2. **Given** a faixa de risco medio configurada para aprovar, **When** o gestor a altera para
   negar, **Then** a proxima transacao de risco medio e negada sem reinicio.
3. **Given** uma tentativa de configuracao em que nenhuma faixa cobre os valores mais altos,
   **When** submetida, **Then** o sistema rejeita a configuracao e mantem a vigente.
4. **Given** uma tentativa de configuracao sem credencial administrativa, **When**
   submetida, **Then** o sistema rejeita e a configuracao vigente permanece.

---

### User Story 7 - Auditar as decisoes tomadas (Priority: P3)

Um investigador de fraude precisa reconstruir por que uma transacao foi aprovada ou negada:
qual pontuacao foi atribuida, qual classificacao resultou, quais regras foram acionadas e o
que as listas informaram no momento da analise.

**Why this priority**: nao participa do fluxo de decisao e nao afeta a resposta ao cliente,
mas e o que permite contestacao, apuracao e evolucao das regras. Sem trilha, nenhuma decisao
e explicavel depois.

**Independent Test**: submeter uma transacao, aguardar o registro da trilha e verificar que
ela contem pontuacao, classificacao, decisao, regras acionadas e resultado das listas.

**Acceptance Scenarios**:

1. **Given** uma transacao analisada, **When** a trilha e consultada, **Then** contem a
   pontuacao, a classificacao, a decisao e o identificador de correlacao da solicitacao.
2. **Given** uma transacao analisada, **When** a trilha e consultada, **Then** contem quais
   regras foram acionadas e o que cada lista informou.
3. **Given** o registro da trilha indisponivel, **When** uma transacao e analisada, **Then**
   o cliente ainda recebe sua decisao normalmente.
4. **Given** uma transacao analisada, **When** os registros de log do sistema sao
   inspecionados, **Then** CPF, IP e identificador de dispositivo aparecem mascarados.

---

### Edge Cases

- **Valor de transacao zero ou negativo**: nenhuma faixa de valor cobre esse intervalo. A
  solicitacao e rejeitada como invalida, em vez de produzir a pontuacao minima.
- **Pontuacao acumulada zero ou negativa**: subtracoes podem levar a soma a zero ou abaixo.
  A pontuacao final e o maior valor entre 1 e a soma, com o piso aplicado uma unica vez ao
  final, nunca a resultados intermediarios.
- **Regra com operador OU e multiplas condicoes verdadeiras**: a acao e aplicada uma unica
  vez, nao uma vez por condicao satisfeita.
- **Servico de listas indisponivel**: a analise prossegue tratando todas as variaveis como
  ausentes de qualquer lista. A transacao e avaliada pelas regras de valor.
- **Motor de decisao indisponivel**: sem pontuacao nao existe decisao. A solicitacao falha
  informando indisponibilidade temporaria, em vez de retornar uma negativa que o cliente
  interpretaria como decisao de risco.
- **Tipo de transacao desconhecido**: nao existindo conjunto especifico, aplica-se apenas o
  conjunto padrao.
- **Conjunto de regras vazio para o tipo e para o padrao**: nenhuma regra e acionada, a soma
  e zero e a pontuacao final e o piso 1, resultando em risco baixo.
- **Faixas de valor com limites duplicados**: configuracao ambigua e rejeitada ao ser
  carregada, preservando a configuracao anterior em vigor.
- **Consulta simultanea de variavel em lista permissiva e restritiva**: ambos os sinais sao
  reportados e ambas as regras correspondentes sao aplicadas.
- **Valor com mais de duas casas decimais**: rejeitado como invalido. Centavo e a menor unidade
  monetaria representavel; aceitar fracoes de centavo introduziria arredondamento silencioso na
  comparacao com os limites de faixa.
- **Tentativa de cadastrar chave ja existente no mesmo tipo de transacao**: rejeitada. Duas
  regras de mesma chave no mesmo conjunto tornariam a composicao indeterminada, pois nao
  haveria como saber qual substituiria a regra padrao.
- **Tipo de transacao com diferenca apenas de caixa** (`pix` e `PIX`): normalizados para
  maiusculas, portanto tratados como o mesmo tipo. Evita conjuntos de regras duplicados por
  divergencia de digitacao.

## Requirements *(mandatory)*

### Functional Requirements

#### Analise de risco

- **FR-001**: O sistema MUST aceitar solicitacoes de analise de risco contendo CPF, endereco
  IP, identificador de dispositivo, tipo de transacao e valor da transacao.
- **FR-002**: O sistema MUST aceitar o CPF apenas como 11 digitos sem mascara, MUST validar
  seus digitos verificadores e MUST rejeitar sequencias de digito repetido (que satisfazem o
  calculo do digito verificador mas nao correspondem a CPF valido).
- **FR-003**: O sistema MUST rejeitar solicitacoes com valor de transacao menor ou igual a
  zero, ou com mais de duas casas decimais.
- **FR-003a**: O sistema MUST tratar valores monetarios com aritmetica decimal exata em todo o
  percurso, MUST NOT usar representacao de ponto flutuante binario para valor ou para limites
  de faixa.
- **FR-003b**: O sistema MUST aceitar qualquer tipo de transacao como texto livre normalizado
  para maiusculas, sem enumeracao fechada, de modo que novos tipos passem a ser atendidos sem
  alteracao de codigo.
- **FR-004**: O sistema MUST consultar a presenca de CPF, IP e identificador de dispositivo
  nas listas antes de calcular a pontuacao.
- **FR-005**: O sistema MUST obter a pontuacao de risco a partir do motor de decisao,
  fornecendo os dados da transacao e o resultado das listas.
- **FR-006**: O sistema MUST classificar a pontuacao recebida em uma faixa de risco e derivar
  dela a decisao.
- **FR-007**: O sistema MUST responder ao cliente exclusivamente com a decisao (`APROVADA` ou
  `NEGADA`).
- **FR-008**: O sistema MUST NOT expor a pontuacao, a classificacao de risco ou as regras
  acionadas em qualquer parte da resposta ao cliente, incluindo cabecalhos e mensagens de
  erro.
- **FR-009**: O sistema MUST aceitar um identificador de correlacao fornecido pelo cliente e,
  quando ausente, gerar um; e MUST propaga-lo por todos os componentes envolvidos na analise.

#### Listas restritivas e permissivas

- **FR-010**: O sistema MUST informar, para cada variavel consultada, se ela consta em lista
  permissiva, em lista restritiva, em ambas ou em nenhuma.
- **FR-011**: O sistema MUST permitir que um CPF conste simultaneamente em lista permissiva e
  restritiva.
- **FR-012**: O sistema MUST restringir IP e identificador de dispositivo a listas
  restritivas.
- **FR-013**: O sistema MUST retornar o resultado das tres variaveis de uma transacao em uma
  unica consulta.
- **FR-014**: O sistema MUST permitir a carga e a atualizacao de entradas nas listas por
  interface administrativa, com efeito imediato nas consultas subsequentes.
- **FR-015**: O sistema MUST suportar prazo de validade para entradas de lista de IP e MUST
  tratar entrada expirada como ausente.
- **FR-016**: O sistema MUST registrar, para cada pertinencia de lista, a lista de origem e a
  situacao da entrada.

#### Motor de decisao

- **FR-017**: O sistema MUST oferecer consulta, inclusao, alteracao e exclusao de regras de
  decisao.
- **FR-018**: Alteracoes em regras MUST surtir efeito nas analises subsequentes sem nova
  versao da aplicacao e sem reinicio de servico.
- **FR-019**: O sistema MUST NOT conter regras de negocio embutidas em codigo-fonte; todo o
  conjunto de regras, inclusive o padrao, MUST ser dado gerenciavel.
- **FR-020**: O sistema MUST manter conjuntos de regras especificos por tipo de transacao,
  alem de um conjunto padrao aplicavel a qualquer tipo.
- **FR-021**: O sistema MUST compor o conjunto efetivo de um tipo partindo do conjunto padrao
  e aplicando as regras especificas: mesma chave substitui, chave inexistente acrescenta, e
  regra padrao sem correspondente permanece ativa.
- **FR-022**: Regras sem chave declarada MUST ser sempre aditivas e MUST NOT substituir outra
  regra.
- **FR-022a**: A chave de sobreposicao MUST ser unica dentro de um mesmo conjunto, isto e, por
  par (tipo de transacao, chave). Regras sem chave MUST NOT estar sujeitas a essa restricao.
- **FR-023**: O sistema MUST suportar regras de faixa de valor mutuamente exclusivas, das
  quais exatamente uma se aplica a cada transacao de valor positivo.
- **FR-023a**: O sistema MUST suportar multiplas escadas de faixas independentes, identificadas
  por nome. As invariantes de integridade — exatamente uma faixa sem limite superior e limites
  superiores distintos — MUST ser verificadas por escada, nao globalmente. Uma escada sem
  nenhuma regra no conjunto efetivo simplesmente nao contribui pontos.
- **FR-024**: O sistema MUST suportar regras condicionais cujas condicoes se combinam por
  operador logico `E` ou `OU`, assumindo `E` quando omitido.
- **FR-025**: Uma regra com operador `OU` MUST aplicar sua acao uma unica vez, independente de
  quantas condicoes forem satisfeitas.
- **FR-026**: O sistema MUST processar todas as regras do conjunto efetivo antes de compor a
  pontuacao final.
- **FR-027**: O sistema MUST calcular a pontuacao de forma cumulativa, somando e subtraindo os
  pontos das acoes das regras acionadas.
- **FR-028**: A pontuacao final MUST ser o maior valor entre 1 e a soma acumulada, com o piso
  aplicado uma unica vez ao final da avaliacao.
- **FR-029**: O motor MUST retornar a pontuacao numerica e MUST NOT retornar classificacao de
  risco nem decisao — essas sao responsabilidade de quem orquestra. O motor PODE acompanhar a
  pontuacao do detalhamento das regras acionadas, destinado exclusivamente a trilha de auditoria
  e a depuracao; quem orquestra MUST NOT propagar esse detalhamento ao cliente.
- **FR-030**: O sistema MUST rejeitar cadastro de regra com campo obrigatorio ausente ou valor
  de enumeracao desconhecido, preservando o conjunto vigente.

#### Faixas de score

- **FR-031**: O sistema MUST armazenar as faixas de classificacao de risco fora do
  codigo-fonte, com seus limites e a decisao associada a cada faixa.
- **FR-032**: O sistema MUST permitir consulta e alteracao das faixas por interface
  administrativa, com efeito nas analises subsequentes sem reinicio.
- **FR-033**: O sistema MUST rejeitar configuracao de faixas que deixe qualquer pontuacao
  possivel sem cobertura, preservando a configuracao vigente.

#### Auditoria e observabilidade

- **FR-034**: O sistema MUST registrar, para cada analise concluida, a pontuacao, a
  classificacao, a decisao, as regras acionadas, o resultado das listas e o identificador de
  correlacao.
- **FR-035**: O registro da trilha MUST ocorrer fora do caminho critico da resposta ao
  cliente, e sua indisponibilidade MUST NOT impedir a entrega da decisao.
- **FR-036**: O sistema MUST emitir registros de log estruturados em todos os componentes.
- **FR-037**: CPF, IP e identificador de dispositivo MUST aparecer mascarados nos registros de
  log.
- **FR-038**: A trilha de auditoria MUST reter o CPF sem mascaramento, por ser base de
  investigacao e de defesa em contestacao.

#### Resiliencia

- **FR-039**: Quando a consulta de listas falhar, o sistema MUST prosseguir a analise tratando
  todas as variaveis como ausentes de qualquer lista.
- **FR-040**: Quando a obtencao da pontuacao falhar, o sistema MUST informar indisponibilidade
  temporaria e MUST NOT retornar decisao de negocio.
- **FR-041**: Toda chamada entre componentes MUST ter limite de tempo explicito.

#### Acesso administrativo

- **FR-042**: As interfaces administrativas de regras, faixas e carga de listas MUST exigir
  credencial; a interface de analise de risco MUST permanecer acessivel sem credencial.

### Key Entities

- **Solicitacao de Analise**: dados da transacao a avaliar — CPF, IP, identificador de
  dispositivo, tipo e valor. Nao e persistida como entidade propria; e o insumo da analise.
- **Resultado de Listas**: para cada variavel da transacao, os sinais de presenca em lista
  permissiva e restritiva, com a lista de origem e a situacao de cada pertinencia.
- **Entrada de Lista**: uma variavel (CPF, IP ou dispositivo) e suas pertinencias. Entradas de
  IP possuem prazo de validade.
- **Regra de Decisao**: predicado avaliavel mais uma acao de pontuacao. Possui chave de
  sobreposicao, tipo de transacao ao qual pertence, e assume uma de duas naturezas: faixa de
  valor mutuamente exclusiva ou condicional cumulativa.
- **Condicao**: para regras condicionais, a triade campo avaliado, operador de comparacao e
  valor de referencia.
- **Acao**: operacao de pontuacao (somar ou subtrair) e quantidade de pontos.
- **Conjunto Efetivo de Regras**: resultado da composicao entre o conjunto padrao e o
  conjunto do tipo de transacao, pela chave de sobreposicao. E derivado, nao armazenado.
- **Faixa de Score**: classificacao de risco, limite de pontuacao e decisao associada.
- **Decisao**: resultado da analise — aprovar ou negar.
- **Trilha de Decisao**: registro historico de uma analise concluida, com pontuacao,
  classificacao, decisao, regras acionadas, resultado das listas e correlacao.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma aplicacao cliente obtem a decisao de risco de uma transacao em menos de 150
  milissegundos no percentil 95, considerando a consulta de listas e o calculo de pontuacao.
- **SC-002**: Uma alteracao de regra feita por um analista passa a valer para novas transacoes
  em menos de 5 segundos, sem que nenhum servico seja reiniciado.
- **SC-003**: Uma alteracao nas faixas de risco passa a valer para novas transacoes em menos
  de 5 segundos, sem reinicio.
- **SC-004**: Um analista consegue criar, alterar, consultar e remover uma regra usando apenas
  a interface administrativa, sem assistencia de engenharia.
- **SC-005**: A consulta de presenca nas listas responde em tempo praticamente constante
  independentemente do volume de entradas cadastradas, sustentando na ordem de milhoes de
  entradas.
- **SC-006**: Adicionar novos tipos de transacao nao exige alteracao de codigo e nao degrada o
  tempo de resposta das analises dos tipos existentes.
- **SC-007**: Com o servico de listas indisponivel, a plataforma continua decidindo
  transacoes, aplicando as regras que nao dependem de listas.
- **SC-008**: Nenhuma resposta ao cliente, em nenhum cenario de sucesso ou de erro, contem
  pontuacao ou classificacao de risco.
- **SC-009**: 100% das analises concluidas produzem um registro de trilha recuperavel,
  contendo pontuacao, classificacao, decisao e regras acionadas.
- **SC-010**: Nenhum registro de log contem CPF, IP ou identificador de dispositivo em forma
  completa.
- **SC-011**: A logica de decisao e verificavel de forma isolada, sem banco de dados e sem
  rede, e o conjunto de testes automatizados cobre no minimo 90% do codigo, falhando a
  construcao abaixo desse limite.
- **SC-012**: Cada cenario de composicao de regras descrito no enunciado — substituicao por
  chave, heranca do padrao, acrescimo de chave nova, regra aditiva sem chave — possui teste
  automatizado nomeado que o demonstra.

## Assumptions

- **Contradicao do enunciado resolvida**: o enunciado descreve, em "Processamento", que o
  motor traduz a pontuacao em classificacao; e, no passo 5 do fluxo, que o motor retorna
  apenas a pontuacao e a classificacao ocorre depois. Adotou-se o passo 5, por ser a
  descricao mais explicita e por manter a responsabilidade de politica de risco fora do
  motor de calculo.
- **Nomenclatura padronizada em portugues**: o enunciado alterna `op_type` e `tx_type` e mistura
  `snake_case` nos campos de entrada com `camelCase` no exemplo de regras. Os contratos foram
  padronizados em portugues com case consistente por camada.
- **Conjunto padrao e dado, nao codigo**: o enunciado menciona carga de regras padrao no
  startup da aplicacao. Interpretou-se "carga" como semeadura de dados, nao como regras
  compiladas: o conjunto padrao e um tipo de transacao como qualquer outro e e gerenciado pelo
  mesmo CRUD.
- **Faixas declaradas por limite superior**: cada faixa e modelada apenas por seu limite
  superior, sendo o limite inferior o teto da faixa anterior. O ganho nao e cobrir o intervalo
  entre `300,00` e `300,01` do enunciado — com a entrada limitada a duas casas decimais, esse
  intervalo e inalcancavel. O ganho e estrutural: ao compor o conjunto padrao com o especifico
  por chave, uma faixa redefinida com outro limite nunca produz lacuna nem sobreposicao com as
  faixas herdadas. Com limites inferior e superior declarados, redefinir a primeira faixa para
  `0,01`–`500,00` a sobreporia a segunda faixa herdada (`300,01`–`5.000,00`) e a transacao
  pontuaria duas vezes. O modelo por teto torna esse estado inexpressavel.
- **Validacao de regras limitada a estrutura**: o cadastro verifica presenca de campos
  obrigatorios e validade das enumeracoes, mas nao a compatibilidade semantica entre campo,
  operador e valor. Uma regra logicamente inutil pode ser cadastrada e simplesmente nunca sera
  acionada.
- **Idempotencia fora de escopo**: cada solicitacao e uma analise nova. O identificador de
  correlacao oferece rastreabilidade, nao deduplicacao. Um ambiente produtivo exigiria chave
  de idempotencia com janela de retencao.
- **Analise de risco sem autenticacao**: assume-se chamada interna ao cluster. Apenas as
  interfaces administrativas exigem credencial. Um ambiente produtivo usaria autenticacao
  mutua ou autorizacao delegada na malha de servicos.
- **Metas de desempenho assumidas**: o enunciado pede baixa latencia e alto volume sem
  numeros. Adotou-se 150 ms no percentil 95 para a analise completa e propagacao de
  configuracao em ate 5 segundos, por serem ordens de grandeza usuais em autorizacao
  transacional.
- **Volume das listas**: assume-se ordem de milhoes de entradas, o que orienta a escolha de
  consulta por chave em tempo constante e a ausencia de replicacao integral das listas em
  memoria.
- **Expurgo de entradas expiradas**: assume-se que a remocao fisica de entradas de lista
  vencidas pode ser diferida pelo mecanismo de armazenamento; por isso a expiracao e sempre
  verificada tambem no momento da leitura.
