package br.com.acme.listas.dominio;

import java.time.Instant;

/**
 * Resultado da consulta de uma variavel.
 *
 * <p>Pertinencia nula significa "nao consta". Pertinencia inativa ou expirada tambem e reportada
 * como nula: para o consumidor, entrada inefetiva e indistinguivel de inexistente — e deve ser,
 * porque tratar de outra forma exigiria que cada consumidor reimplementasse a regra de expiracao.
 */
public record ResultadoVariavel(String valor, Pertinencia permissiva, Pertinencia restritiva) {

    public static ResultadoVariavel ausente(String valor) {
        return new ResultadoVariavel(valor, null, null);
    }

    public static ResultadoVariavel de(String valor, Pertinencia permissiva, Pertinencia restritiva,
                                       Instant momento) {
        return new ResultadoVariavel(
                valor,
                efetivaOuNula(permissiva, momento),
                efetivaOuNula(restritiva, momento));
    }

    private static Pertinencia efetivaOuNula(Pertinencia pertinencia, Instant momento) {
        return pertinencia != null && pertinencia.efetivaEm(momento) ? pertinencia : null;
    }

    public boolean constaEmPermissiva() {
        return permissiva != null;
    }

    public boolean constaEmRestritiva() {
        return restritiva != null;
    }
}
