package br.com.acme.motordecisao.infraestrutura.mensageria;

import java.time.Instant;
import java.util.UUID;

/**
 * Sinal de que as regras mudaram. Nao transporta as regras.
 *
 * <p>O consumidor recarrega do Postgres, que e a fonte de verdade. Isso mantem o evento pequeno,
 * idempotente e imune a reordenacao: duas recargas seguidas convergem ao mesmo estado.
 */
public record EventoRegrasAtualizadas(
        UUID idEvento,
        Instant ocorridoEm,
        String tipoTransacaoAfetado,
        String operacao) {

    public static EventoRegrasAtualizadas de(String tipoTransacaoAfetado, String operacao) {
        return new EventoRegrasAtualizadas(UUID.randomUUID(), Instant.now(), tipoTransacaoAfetado, operacao);
    }
}
