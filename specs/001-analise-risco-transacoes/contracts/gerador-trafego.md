# Contrato — gerador-trafego (auxiliar)

Porta padrao `8084`. OpenAPI em `/swagger-ui.html`.

Servico **auxiliar**: nao participa do fluxo de analise e sua ausencia nao afeta nenhum requisito
funcional. Existe para tornar SC-001 (150 ms no percentil 95) verificavel — uma meta de latencia so
e afirmavel se for medida.

Sobe **ocioso**: nao gera trafego ate receber um comando explicito.

Todas as rotas exigem `X-Api-Key`.

---

## POST /v1/cargas

Inicia uma carga. Responde imediatamente, sem aguardar a conclusao.

**Request** — todos os campos opcionais:

```json
{
  "duracaoSegundos": 30,
  "requisicoesPorSegundo": 50,
  "semente": 42
}
```

| Campo | Padrao | Regra |
|---|---|---|
| `duracaoSegundos` | 30 | entre 1 e 600 |
| `requisicoesPorSegundo` | 50 | entre 1 e 1000 |
| `semente` | aleatoria | fixa o sorteio, tornando a carga reproduzivel |

**202 Accepted** — devolve o relatorio inicial, com `situacao: EM_EXECUCAO`.

**409 Conflict** — ja existe carga em andamento.

```json
{
  "type": "https://acme.com.br/erros/carga-em-andamento",
  "title": "Carga já em andamento",
  "status": 409,
  "detail": "Ja existe uma carga em andamento. Aguarde a conclusao ou interrompa antes de iniciar outra."
}
```

Uma carga por vez e deliberado: duas execucoes simultaneas misturariam amostras de latencia, e o
percentil resultante nao descreveria nenhuma das duas.

**400 Bad Request** — parametros fora dos limites, em RFC 9457.

---

## GET /v1/cargas/atual

Relatorio da carga em andamento ou, se nenhuma estiver ativa, da ultima concluida.

**200 OK**

```json
{
  "situacao": "CONCLUIDA",
  "duracaoSegundosPlanejada": 30,
  "duracaoRealMilissegundos": 30083,
  "requisicoesPorSegundoPlanejada": 50,
  "requisicoes": { "total": 1500, "sucesso": 1500, "erro": 0 },
  "latenciaMs": { "p50": 66, "p95": 129, "p99": 614, "media": 85.04, "maxima": 624 },
  "decisoes": { "APROVADA": 1133, "NEGADA": 367 },
  "porTipoTransacao": { "PIX": 903, "CARTAO": 366, "TED": 231 },
  "errosPorMotivo": {},
  "metaP95Ms": 150,
  "metaAtingida": true
}
```

`situacao` ∈ { `EM_EXECUCAO`, `CONCLUIDA`, `INTERROMPIDA` }.

**204 No Content** — nenhuma carga foi executada ainda.

### O que cada campo permite verificar

| Campo | Verifica |
|---|---|
| `requisicoes.erro` | zero prova que todo CPF gerado tem digito verificador valido. Um gerador ingenuo receberia `400` em tudo e mediria a latencia da validacao de entrada |
| `metaAtingida` | resposta objetiva a SC-001 |
| `decisoes` | aprovadas **e** negadas provam trafego calibrado; 100% de aprovacao indicaria carga que nao exercita a composicao de regras |
| `porTipoTransacao` | confirma que os tres tipos com conjunto proprio de regras foram percorridos |
| `errosPorMotivo` | agrupa falhas por causa (`HTTP_503`, `RESPOSTA_VAZIA`), em vez de listar milhares de mensagens |

### Metodo do percentil

**Nearest-rank**: para a amostra ordenada de tamanho `n`, o percentil `p` e o elemento na posicao
`ceil(p/100 × n)`, contada a partir de 1. Sem interpolacao — o valor devolvido e sempre uma medicao
que de fato aconteceu.

O metodo esta declarado porque percentil e ambiguo: existem varias definicoes, e bibliotecas
diferentes devolvem valores distintos para a mesma amostra. Um p95 sem o metodo declarado nao e
verificavel por terceiros.

---

## DELETE /v1/cargas/atual

Interrompe a carga em andamento.

**200 OK** — devolve o relatorio parcial, com `situacao: INTERROMPIDA`.

**204 No Content** — nao havia carga em andamento.

---

## Perfil do trafego gerado

Calibrado para exercitar o dominio, nao uniformemente aleatorio.

| Dimensao | Distribuicao |
|---|---|
| Tipo | 60% PIX · 25% CARTAO · 15% TED |
| Valor | 40% ate R$300 · 35% ate R$5.000 · 15% ate R$20.000 · 10% acima |
| CPF | 80% gerado com DV valido · 20% da massa semeada, que consta em listas |
| IP e dispositivo | 10% cada da massa semeada, em lista restritiva |

Sem essa calibragem, todo trafego cairia na mesma faixa de valor e quase nunca em lista restritiva
— mediria a latencia de um unico caminho de codigo e reportaria 100% de aprovacao.

Os identificadores conhecidos vem de `infra/seed-listas.sh`.

---

## GET /actuator/health

Sem autenticacao.
