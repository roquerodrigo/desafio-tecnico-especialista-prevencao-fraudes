#!/bin/sh
# Popula as listas com a massa usada nos cenarios do quickstart.
#
# Entra pelo endpoint de carga, que e o caminho real de producao — o seed exercita o proprio
# contrato da API e serve como teste de fumaca da subida.

set -e

apk add --no-cache curl >/dev/null 2>&1

echo "Aguardando o servico de listas..."
until curl -sf "${URL_LISTAS}/actuator/health" >/dev/null 2>&1; do
  sleep 2
done

echo "Carregando entradas nas listas..."
curl -sf -X PUT "${URL_LISTAS}/v1/listas" \
  -H 'Content-Type: application/json' \
  -H "X-Api-Key: ${API_KEY}" \
  -d '{
    "entradas": [
      {
        "tipo": "CPF",
        "valor": "11144477735",
        "restritiva": { "idLista": "4a7e0000-0000-4000-8000-000000000001", "situacao": "ATIVA" }
      },
      {
        "tipo": "CPF",
        "valor": "12345678909",
        "permissiva": { "idLista": "9c1f0000-0000-4000-8000-000000000001", "situacao": "ATIVA" }
      },
      {
        "tipo": "CPF",
        "valor": "00000000191",
        "permissiva": { "idLista": "9c1f0000-0000-4000-8000-000000000001", "situacao": "ATIVA" },
        "restritiva": { "idLista": "4a7e0000-0000-4000-8000-000000000001", "situacao": "ATIVA" }
      },
      {
        "tipo": "IP",
        "valor": "198.51.100.66",
        "restritiva": { "idLista": "77b20000-0000-4000-8000-000000000001", "situacao": "ATIVA" },
        "expiraEm": "2027-12-31T23:59:59Z"
      },
      {
        "tipo": "IP",
        "valor": "198.51.100.77",
        "restritiva": { "idLista": "77b20000-0000-4000-8000-000000000001", "situacao": "ATIVA" },
        "expiraEm": "2020-01-01T00:00:00Z"
      },
      {
        "tipo": "DISPOSITIVO",
        "valor": "99999999-9999-4999-8999-999999999999",
        "restritiva": { "idLista": "88c30000-0000-4000-8000-000000000001", "situacao": "ATIVA" }
      }
    ]
  }'

echo ""
echo "Massa carregada:"
echo "  CPF 11144477735 -> lista restritiva"
echo "  CPF 12345678909 -> lista permissiva"
echo "  CPF 00000000191 -> ambas as listas"
echo "  IP  198.51.100.66 -> restritiva (valida)"
echo "  IP  198.51.100.77 -> restritiva (expirada; deve ser tratada como ausente)"
echo "  DISPOSITIVO 99999999-9999-4999-8999-999999999999 -> restritiva"
