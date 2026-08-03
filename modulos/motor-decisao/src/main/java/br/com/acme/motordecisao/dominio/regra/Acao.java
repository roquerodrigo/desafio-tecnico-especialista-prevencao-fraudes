package br.com.acme.motordecisao.dominio.regra;

import java.util.Objects;

/**
 * Pontuacao que uma regra aplica quando acionada.
 *
 * <p>{@code pontos} e sempre positivo; o sinal vem do {@link TipoAcao}. Guardar "subtrair 200"
 * em vez de "somar -200" mantem a intencao explicita no cadastro e impede a combinacao
 * ambigua de subtrair um valor negativo.
 */
public record Acao(TipoAcao tipo, int pontos) {

    public Acao {
        Objects.requireNonNull(tipo, "tipo da acao e obrigatorio");
        if (pontos <= 0) {
            throw new IllegalArgumentException(
                    "pontos da acao devem ser positivos; o sinal e determinado pelo tipo. Recebido: " + pontos);
        }
    }

    public static Acao somar(int pontos) {
        return new Acao(TipoAcao.SOMAR, pontos);
    }

    public static Acao subtrair(int pontos) {
        return new Acao(TipoAcao.SUBTRAIR, pontos);
    }

    /**
     * Contribuicao desta acao para a soma acumulada, ja com sinal.
     */
    public int pontosComSinal() {
        return tipo == TipoAcao.SOMAR ? pontos : -pontos;
    }
}
