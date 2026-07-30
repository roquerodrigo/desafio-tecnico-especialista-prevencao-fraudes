# ADR 0003 — Conjunto padrão como dado, não código

**Status**: aceito · **Data**: 2026-07-29

## Contexto

O enunciado diz que "durante o startup da aplicação, o sistema deve carregar um conjunto de regras
padrão". Lido literalmente, isso sugere regras compiladas no código, criadas na subida.

Mas o mesmo enunciado exige que as regras sejam "dinâmicas e interpretáveis em tempo de execução,
permitindo que sejam incluídas, alteradas ou removidas sem novo deploy", para que "usuários de
negócio possam gerenciar as regras diretamente".

As duas coisas são incompatíveis se o conjunto padrão for código: alterar a pontuação de uma regra
padrão exigiria engenheiro e janela de deploy.

## Decisão

Nenhuma regra existe em código-fonte. O conjunto padrão é o registro com
`tipoTransacao = 'PADRAO'` — um tipo de transação como qualquer outro, cadastrado e editável pelo
mesmo CRUD.

A carga inicial é uma migration Flyway que semeia **dado**.

## Consequências

- Um único CRUD gerencia tudo. Não há caminho especial para "regras do sistema".
- A composição por chave de sobreposição opera entre dois registros do banco, não entre banco e
  código — o que a torna uniformemente testável.
- Alterar uma regra padrão é um `PUT`, com efeito nas próximas transações.
- Consequência aceita: nada impede um operador de remover todas as regras padrão. O sistema não
  quebra (o motor devolve o piso 1 e o risco fica baixo), mas a decisão perde sentido de negócio.
  Um ambiente produtivo teria autorização por papel nas rotas administrativas.

## Alternativas consideradas

**Regras padrão em código, específicas em banco** — mais fiel à leitura literal do enunciado.
Rejeitado por criar dois mecanismos para o mesmo conceito e por deixar metade das regras fora do
alcance do usuário de negócio.
