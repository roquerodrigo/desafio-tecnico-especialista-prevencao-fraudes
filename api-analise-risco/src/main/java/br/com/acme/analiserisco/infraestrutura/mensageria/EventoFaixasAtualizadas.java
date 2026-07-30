package br.com.acme.analiserisco.infraestrutura.mensageria;

import java.time.Instant;
import java.util.UUID;

/**
 * Sinal de que as faixas mudaram. Como nos demais eventos de invalidacao, nao transporta a
 * configuracao — o consumidor rele do Postgres, que e a fonte de verdade.
 */
public record EventoFaixasAtualizadas(UUID idEvento, Instant ocorridoEm) {

    public static EventoFaixasAtualizadas agora() {
        return new EventoFaixasAtualizadas(UUID.randomUUID(), Instant.now());
    }
}
