package br.com.acme.listas.dominio;

import java.time.Instant;
import java.util.Objects;

/**
 * Pertinencia de uma variavel a uma lista. A presenca deste objeto significa que a variavel consta
 * — nao ha flag booleana redundante.
 *
 * <p>Uma pertinencia so e <b>efetiva</b> se estiver ativa e nao expirada. A verificacao de
 * expiracao acontece na leitura, sempre: a AWS documenta que o expurgo por TTL ocorre "within a few
 * days" da expiracao, portanto confiar apenas no TTL deixaria entradas vencidas ainda bloqueando.
 */
public record Pertinencia(String idLista, Situacao situacao, Instant dataInclusao, Instant expiraEm) {

    public Pertinencia {
        Objects.requireNonNull(idLista, "idLista e obrigatorio");
        Objects.requireNonNull(situacao, "situacao e obrigatoria");
    }

    public static Pertinencia ativa(String idLista) {
        return new Pertinencia(idLista, Situacao.ATIVA, Instant.now(), null);
    }

    public boolean efetivaEm(Instant momento) {
        if (situacao != Situacao.ATIVA) {
            return false;
        }
        return expiraEm == null || expiraEm.isAfter(momento);
    }

    public enum Situacao {
        ATIVA,
        INATIVA
    }
}
