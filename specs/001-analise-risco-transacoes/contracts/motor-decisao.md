# Contrato — motor-decisao

Porta padrao `8082`. OpenAPI em `/swagger-ui.html`.

---

## POST /v1/scores

Calcula o score de risco. Publico (intra-cluster).

**Request**

```json
{
  "tipoTransacao": "PIX",
  "valorTransacao": 1500.00,
  "cpfEmListaPermissiva": false,
  "cpfEmListaRestritiva": true,
  "ipEmListaRestritiva": false,
  "dispositivoEmListaRestritiva": false
}
```

Os quatro booleanos sao obrigatorios. O motor nao consulta listas — ele recebe o resultado ja
apurado. Isso mantem o motor puro (mesma entrada, mesmo score) e testavel sem rede.

O corpo **nao carrega CPF, IP nem identificador de dispositivo**: o motor decide sobre pertinencia
ja apurada, e dado pessoal que ele nao usa nao deve atravessar essa fronteira.

**200 OK**

```json
{
  "score": 600,
  "regrasAcionadas": [
    { "id": "…", "chave": "faixa_valor_2", "descricao": "PIX de R$300,01 a R$5.000,00", "acao": "SOMAR", "pontos": 400 },
    { "id": "…", "chave": "cpf_lista_restritiva", "descricao": "PIX: CPF em lista restritiva", "acao": "SOMAR", "pontos": 200 }
  ]
}
```

Cada chave aparece no maximo uma vez: a composicao por chave de sobreposicao garante que apenas
uma regra por chave chega ao calculo, e cada escada contribui com uma unica faixa.

`score` e sempre inteiro ≥ 1 (piso aplicado uma unica vez ao final).

`regrasAcionadas` existe para a trilha de auditoria e para depuracao. **A api-analise-risco
MUST NOT propagar esse campo ao cliente** — ela o encaminha apenas ao evento de auditoria.

**400 Bad Request** — validacao de entrada.

**503 Service Unavailable** — cache de regras indisponivel (invariante violada na carga, e
nenhum cache anterior valido). Sem conjunto de regras confiavel, o motor prefere nao pontuar a
pontuar errado.

---

## GET /v1/regras

Lista regras. Requer `X-Api-Key`.

Parametros de consulta:

| Parametro | Efeito |
|---|---|
| `tipoTransacao` | filtra pelo conjunto cadastrado daquele tipo |
| `efetivo=true` | devolve o **conjunto efetivo** do tipo, apos composicao com `PADRAO` |

`efetivo=true` e o endpoint que torna a chave de sobreposicao observavel: mostra o que de fato
sera aplicado, com a origem de cada regra.

**200 OK** (com `?tipoTransacao=CARTAO&efetivo=true`)

```json
{
  "tipoTransacao": "CARTAO",
  "regras": [
    { "chave": "faixa_valor_1", "origem": "CARTAO", "escada": "VALOR_TRANSACAO", "limiteSuperior": 300.00,   "acao": { "tipo": "SOMAR", "pontos": 350 } },
    { "chave": "faixa_valor_2", "origem": "PADRAO", "escada": "VALOR_TRANSACAO", "limiteSuperior": 5000.00,  "acao": { "tipo": "SOMAR", "pontos": 300 } },
    { "chave": "faixa_valor_3", "origem": "PADRAO", "escada": "VALOR_TRANSACAO", "limiteSuperior": 20000.00, "acao": { "tipo": "SOMAR", "pontos": 400 } },
    { "chave": "faixa_valor_4", "origem": "PADRAO", "escada": "VALOR_TRANSACAO", "limiteSuperior": null,     "acao": { "tipo": "SOMAR", "pontos": 500 } }
  ]
}
```

`origem` indica de qual conjunto a regra veio: `CARTAO` substituiu `faixa_valor_1`; as demais
faixas foram herdadas do `PADRAO`.

---

## POST /v1/regras

Cria uma regra. Requer `X-Api-Key`.

**Regra de escada**

```json
{
  "chave": "faixa_valor_1",
  "tipoTransacao": "CARTAO",
  "descricao": "Cartao ate R$300,00",
  "escada": "VALOR_TRANSACAO",
  "limiteSuperior": 300.00,
  "acao": { "tipo": "SOMAR", "pontos": 350 }
}
```

**Regra condicional**

```json
{
  "chave": "ip_ou_dispositivo_lista_restritiva",
  "tipoTransacao": "PADRAO",
  "descricao": "IP ou dispositivo em lista restritiva",
  "operadorLogico": "OU",
  "condicoes": [
    { "campo": "IP_EM_LISTA_RESTRITIVA",           "operador": "IGUAL", "valor": "true" },
    { "campo": "DISPOSITIVO_EM_LISTA_RESTRITIVA",  "operador": "IGUAL", "valor": "true" }
  ],
  "acao": { "tipo": "SOMAR", "pontos": 400 }
}
```

| Campo | Regra |
|---|---|
| `chave` | opcional; quando presente, unica por `tipoTransacao` |
| `tipoTransacao` | obrigatorio; `PADRAO` designa o conjunto base |
| `escada` | presente ⇒ regra de escada; `condicoes` e `operadorLogico` proibidos |
| `limiteSuperior` | apenas em regra de escada; `null` = sem teto |
| `operadorLogico` | `E` \| `OU`; ausente assume `E` |
| `condicoes` | obrigatoria e nao vazia em regra condicional |
| `acao.tipo` | `SOMAR` \| `SUBTRAIR` |
| `acao.pontos` | inteiro > 0 |

**201 Created** — `Location: /v1/regras/{id}`. Publica `regras.atualizadas`.

**409 Conflict** — chave ja usada nesse `tipoTransacao`.

```json
{
  "type": "https://acme.com.br/erros/chave-duplicada",
  "title": "Chave de sobreposicao ja utilizada",
  "status": 409,
  "detail": "A chave 'faixa_valor_1' ja existe para o tipo de transacao 'CARTAO'",
  "instance": "/v1/regras"
}
```

**422 Unprocessable Entity** — natureza hibrida (escada com condicoes), enum desconhecido,
`pontos` nao positivo.

> A validacao e **estrutural**, nao semantica. `CPF_EM_LISTA_PERMISSIVA MAIOR_QUE 5` e aceito e
> simplesmente nunca sera acionado — decisao registrada como premissa no README.

---

## GET /v1/regras/{id} · PUT /v1/regras/{id} · DELETE /v1/regras/{id}

Requerem `X-Api-Key`. `PUT` substitui integralmente a regra; `DELETE` remove (`204 No Content`).
Ambos publicam `regras.atualizadas`. `404` quando o `id` nao existe.

---

## GET /actuator/health

Sem autenticacao. `readiness` fica `DOWN` enquanto nao houver cache de regras valido carregado.
Se uma recarga falhar por invariante violada, o health reporta degradado mas o servico continua
atendendo com o **cache anterior** — nunca com cache vazio.
