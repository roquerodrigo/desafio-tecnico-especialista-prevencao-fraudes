package br.com.acme.analiserisco.dominio;

import java.util.Objects;

/**
 * Faixa de classificacao de risco, com a decisao associada.
 *
 * <p>A decisao e dado, nao codigo: passar a negar risco medio e um {@code PUT}, sem deploy. O
 * enunciado descreve a politica de aprovacao como valida "inicialmente", sinalizando que muda.
 *
 * <p>{@code limiteSuperior} nulo significa sem teto — nunca um sentinela numerico.
 */
public record FaixaScore(ClassificacaoRisco classificacao, Integer limiteSuperior, Decisao decisao) {

    public FaixaScore {
        Objects.requireNonNull(classificacao, "classificacao e obrigatoria");
        Objects.requireNonNull(decisao, "decisao e obrigatoria");
        if (limiteSuperior != null && limiteSuperior <= 0) {
            throw new TabelaFaixasInvalidaException(
                    "limite superior da faixa " + classificacao + " deve ser positivo; recebido: " + limiteSuperior);
        }
    }

    public boolean semTeto() {
        return limiteSuperior == null;
    }
}
