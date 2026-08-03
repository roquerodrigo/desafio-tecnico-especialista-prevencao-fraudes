# Contrato — Eventos Kafka

Tres topicos. Nomenclatura `entidade.evento`.

| Topico | Produtor | Consumidor | Proposito |
|---|---|---|---|
| `regras.atualizadas` | motor-decisao | motor-decisao (todas as replicas) | invalidar cache de regras |
| `faixas.atualizadas` | api-analise-risco | api-analise-risco (todas as replicas) | invalidar cache de faixas |
| `decisoes.registradas` | api-analise-risco | servico-auditoria | persistir trilha |

Topicos declarados em `@Bean NewTopic` no servico que os possui — nao em IaC, para manter o
topico junto do codigo que o usa.

---

## Configuracao de consumo por proposito

Os dois usos tem exigencias opostas, e a configuracao reflete isso.

### Invalidacao de cache (`regras.atualizadas`, `faixas.atualizadas`)

| Propriedade | Valor | Motivo |
|---|---|---|
| `group.id` | `<servico>-cache-${HOSTNAME}` | **unico por instancia**. Group compartilhado entregaria a mensagem a uma so replica — o oposto do fan-out necessario |
| `auto-offset-reset` | `latest` | Postgres e a fonte de verdade; o evento e so um sinal para reler. Reprocessar historico nao agrega |
| particoes | 1 | ordenacao total; volume e desprezivel |

### Trilha de auditoria (`decisoes.registradas`)

| Propriedade | Valor | Motivo |
|---|---|---|
| `group.id` | `auditoria-trilha` | **compartilhado**. Aqui queremos distribuicao de carga, nao fan-out: cada decisao deve ser persistida uma vez |
| `auto-offset-reset` | `earliest` | nenhuma decisao pode ser perdida por reinicio do consumidor |
| particoes | 3 | permite escalar o consumo |
| chave | `idCorrelacao` | garante ordenacao por transacao |

A diferenca entre `group.id` unico e compartilhado nos dois casos e deliberada e e o ponto
central do desenho de mensageria: **fan-out para invalidacao, distribuicao para processamento.**

---

## `regras.atualizadas`

Publicado apos qualquer escrita no CRUD de regras.

```json
{
  "idEvento": "0f8a…",
  "ocorridoEm": "2026-07-29T18:42:11.482Z",
  "tipoTransacaoAfetado": "CARTAO",
  "operacao": "CRIACAO"
}
```

`operacao` ∈ { `CRIACAO`, `ALTERACAO`, `EXCLUSAO` }.

O payload e informativo, **nao transporta as regras**. O consumidor recarrega do Postgres. Isso
mantem o evento pequeno, idempotente e imune a reordenacao: duas recargas seguidas convergem ao
mesmo estado.

`tipoTransacaoAfetado` permite recarga seletiva; a implementacao inicial recarrega tudo, que e
barato para dezenas de regras.

## `faixas.atualizadas`

```json
{
  "idEvento": "3b21…",
  "ocorridoEm": "2026-07-29T18:45:02.109Z"
}
```

Mesma semantica de sinal: o consumidor rele a tabela de faixas.

## `decisoes.registradas`

```json
{
  "idEvento": "7c44…",
  "idCorrelacao": "b19f…",
  "ocorridoEm": "2026-07-29T18:47:33.221Z",
  "cpf": "52998224725",
  "ip": "203.0.113.42",
  "idDispositivo": "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
  "tipoTransacao": "PIX",
  "valorTransacao": 1500.00,
  "score": 600,
  "classificacao": "MEDIO",
  "decisao": "APROVADA",
  "consultaListasDegradada": false,
  "regrasAcionadas": [
    { "chave": "faixa_valor_2", "acao": "SOMAR", "pontos": 400 },
    { "chave": "cpf_lista_restritiva", "acao": "SOMAR", "pontos": 200 }
  ],
  "resultadoListas": {
    "cpfEmListaPermissiva": false,
    "cpfEmListaRestritiva": true,
    "ipEmListaRestritiva": false,
    "dispositivoEmListaRestritiva": false
  }
}
```

Este e o unico lugar do sistema onde score, classificacao e regras acionadas trafegam juntos — e
ele nao e exposto ao cliente.

CPF em claro no payload por decisao explicita: a trilha e base de investigacao. O topico e
interno e o mascaramento aplica-se aos registros de log, nao ao evento.

`consultaListasDegradada` registra que a analise ocorreu sem o sinal de listas. Permite medir
quanto risco a indisponibilidade gerou — informacao que se perderia se a degradacao fosse
silenciosa.

**Idempotencia no consumo**: `uk_trilha_correlacao` no banco garante que reentrega nao duplica
registro. O consumidor trata violacao de unicidade como sucesso.

**Publicacao**: assincrona, fora do caminho critico. Falha na publicacao e registrada em log
mas **nao** impede a resposta ao cliente (FR-035) — a decisao ja foi tomada, e nega-la por falha
de auditoria seria transformar problema de observabilidade em problema de negocio.
