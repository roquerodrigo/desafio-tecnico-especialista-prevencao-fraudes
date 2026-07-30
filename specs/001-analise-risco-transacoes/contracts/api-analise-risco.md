# Contrato — api-analise-risco

Porta padrao `8080`. OpenAPI em `/swagger-ui.html`.

Cabecalhos comuns:

| Cabecalho | Uso |
|---|---|
| `X-Correlation-Id` | opcional na entrada; sempre presente na resposta. Gerado se ausente |
| `X-Api-Key` | obrigatorio apenas nos endpoints administrativos |

Erros seguem RFC 9457 (`application/problem+json`).

---

## POST /v1/analises-risco

Avalia o risco de uma transacao. Publico (chamada intra-cluster).

**Request**

```json
{
  "cpf": "52998224725",
  "ip": "203.0.113.42",
  "idDispositivo": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "tipoTransacao": "PIX",
  "valorTransacao": 1500.00
}
```

| Campo | Tipo | Validacao |
|---|---|---|
| `cpf` | string | 11 digitos, DV valido, nao sequencia repetida |
| `ip` | string | IPv4 ou IPv6 valido |
| `idDispositivo` | string | UUID |
| `tipoTransacao` | string | nao vazio, ate 50 caracteres, normalizado para maiusculas |
| `valorTransacao` | number | > 0, no maximo 2 casas decimais |

**200 OK**

```json
{ "decisao": "APROVADA" }
```

`decisao` ∈ { `APROVADA`, `NEGADA` }. **Nenhum outro campo.** Score e classificacao nao
aparecem no corpo, em cabecalho nem em erro.

**400 Bad Request** — falha de validacao.

```json
{
  "type": "https://acme.com.br/erros/requisicao-invalida",
  "title": "Requisicao invalida",
  "status": 400,
  "detail": "Um ou mais campos estao invalidos",
  "instance": "/v1/analises-risco",
  "erros": [
    { "campo": "cpf", "mensagem": "CPF invalido" },
    { "campo": "valorTransacao", "mensagem": "Valor deve ser maior que zero" }
  ]
}
```

**503 Service Unavailable** — motor de decisao indisponivel (fail-closed). Cabecalho
`Retry-After: 5`.

```json
{
  "type": "https://acme.com.br/erros/analise-indisponivel",
  "title": "Analise temporariamente indisponivel",
  "status": 503,
  "detail": "Nao foi possivel concluir a avaliacao de risco. Tente novamente.",
  "instance": "/v1/analises-risco"
}
```

O `detail` e deliberadamente generico: nao revela qual dependencia falhou.

> Listas indisponiveis **nao** produzem erro. A analise prossegue tratando as variaveis como
> ausentes de qualquer lista e a resposta e `200` normalmente.

---

## GET /v1/faixas-score

Consulta as faixas vigentes. Requer `X-Api-Key`.

**200 OK**

```json
{
  "faixas": [
    { "classificacao": "BAIXO", "limiteSuperior": 399,  "decisao": "APROVADA" },
    { "classificacao": "MEDIO", "limiteSuperior": 699,  "decisao": "APROVADA" },
    { "classificacao": "ALTO",  "limiteSuperior": null, "decisao": "NEGADA"   }
  ]
}
```

`limiteSuperior: null` significa sem teto. Faixas retornam ordenadas por limite crescente, com
a sem teto ao final.

---

## PUT /v1/faixas-score

Substitui integralmente a configuracao de faixas. Requer `X-Api-Key`.

Substituicao total, nao parcial: a configuracao de faixas e um conjunto com invariantes entre os
elementos, e aceitar alteracao de uma faixa isolada permitiria estado intermediario invalido.

**Request** — mesmo formato do `GET`.

**200 OK** — devolve a configuracao aplicada e publica `faixas.atualizadas`.

**422 Unprocessable Entity** — invariante violada.

```json
{
  "type": "https://acme.com.br/erros/configuracao-invalida",
  "title": "Configuracao de faixas invalida",
  "status": 422,
  "detail": "E obrigatoria exatamente uma faixa sem limite superior; foram encontradas 0",
  "instance": "/v1/faixas-score"
}
```

Invariantes verificadas: exatamente uma faixa sem teto; limites distintos; limites positivos;
decisao valida. Violacao ⇒ configuracao vigente preservada.

**401 Unauthorized** — `X-Api-Key` ausente ou invalida.

---

## GET /actuator/health

Sem autenticacao. `readiness` fica `DOWN` enquanto as faixas nao tiverem sido carregadas com
sucesso — servir analise sem tabela de faixas produziria classificacao arbitraria.
