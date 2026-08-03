package br.com.acme.motordecisao.dominio;

import br.com.acme.motordecisao.dominio.regra.Regra;
import java.util.List;

/**
 * Score calculado e as regras que o produziram.
 *
 * <p>{@code regrasAcionadas} existe para a trilha de auditoria e para depuracao. Quem orquestra
 * nao pode propagar esse detalhamento ao cliente (FR-029).
 */
public record ResultadoScore(int score, List<Regra> regrasAcionadas) {

    public ResultadoScore {
        if (score < 1) {
            throw new IllegalArgumentException("score deve ser no minimo 1; recebido: " + score);
        }
        regrasAcionadas = List.copyOf(regrasAcionadas);
    }
}
