# Contrato — servico-listas

Porta padrao `8081`. OpenAPI em `/swagger-ui.html`.

---

## POST /v1/consultas-listas

Consulta as tres variaveis de uma transacao em uma unica chamada. Publico (intra-cluster).

Verbo `POST` e nao `GET` de proposito: os identificadores consultados sao dados pessoais e nao
devem trafegar em query string, onde apareceriam em log de servidor e historico de proxy.

**Request**

```json
{
  "cpf": "52998224725",
  "ip": "203.0.113.42",
  "idDispositivo": "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
}
```

Os tres campos sao opcionais individualmente, mas ao menos um MUST estar presente. Variavel
ausente na requisicao vem ausente na resposta.

**200 OK**

```json
{
  "cpf": {
    "valor": "52998224725",
    "permissiva": { "idLista": "9c1f...", "situacao": "ATIVA" },
    "restritiva": { "idLista": "4a7e...", "situacao": "ATIVA" }
  },
  "ip": {
    "valor": "203.0.113.42",
    "restritiva": null
  },
  "dispositivo": {
    "valor": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
    "restritiva": { "idLista": "77b2...", "situacao": "ATIVA" }
  }
}
```

Semantica: pertinencia `null` = nao consta. Pertinencia presente = consta. O CPF do exemplo
consta em **ambas** as listas, cenario que o enunciado exige suportar.

`ip` e `dispositivo` nunca expoem `permissiva` — apenas CPF admite lista permissiva.

Pertinencia com `situacao` diferente de `ATIVA`, ou com `expiraEm` no passado, retorna `null`:
para o consumidor, entrada inativa ou expirada e indistinguivel de inexistente.

**400 Bad Request** — nenhuma variavel informada, ou formato invalido.

---

## PUT /v1/listas

Carrega ou atualiza entradas. Requer `X-Api-Key`.

Idempotente por variavel: reenviar a mesma entrada sobrescreve, nao duplica.

**Request**

```json
{
  "entradas": [
    {
      "tipo": "CPF",
      "valor": "52998224725",
      "permissiva": { "idLista": "9c1f...", "situacao": "ATIVA" },
      "restritiva": { "idLista": "4a7e...", "situacao": "ATIVA" }
    },
    {
      "tipo": "IP",
      "valor": "203.0.113.42",
      "restritiva": { "idLista": "77b2...", "situacao": "ATIVA" },
      "expiraEm": "2026-08-29T00:00:00Z"
    },
    {
      "tipo": "DISPOSITIVO",
      "valor": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
      "restritiva": { "idLista": "77b2...", "situacao": "ATIVA" }
    }
  ]
}
```

| Campo | Regra |
|---|---|
| `tipo` | `CPF` \| `IP` \| `DISPOSITIVO` |
| `valor` | validado conforme o tipo |
| `permissiva` | somente para `CPF`; rejeitado nos demais |
| `restritiva` | admitido em todos os tipos |
| `expiraEm` | somente para `IP`; instante em UTC, futuro |

**200 OK**

```json
{ "processadas": 3, "ignoradas": 0 }
```

**400 Bad Request** — `permissiva` em IP ou dispositivo; `expiraEm` em CPF ou dispositivo;
`expiraEm` no passado; lote vazio ou acima do limite (500 entradas).

**401 Unauthorized** — `X-Api-Key` ausente ou invalida.

> Nao existe endpoint de recarga. O DynamoDB e fonte compartilhada e a leitura e sempre atual —
> nao ha cache local para invalidar. O requisito de "recarregar as listas quando atualizadas" e
> atendido por ausencia de cache stale, nao por mecanismo de invalidacao.

---

## GET /actuator/health

Sem autenticacao. Inclui verificacao de conectividade com o DynamoDB.
