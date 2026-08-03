package br.com.acme.motordecisao.infraestrutura.web.dto;

import br.com.acme.motordecisao.dominio.ResultadoScore;
import java.util.List;

/**
 * {@code regrasAcionadas} destina-se a trilha de auditoria e a depuracao. Quem orquestra nao pode
 * propagar esse detalhamento ao cliente final (FR-029).
 */
public record CalculoScoreResponse(int score, List<RegraAcionadaResponse> regrasAcionadas) {

    public static CalculoScoreResponse de(ResultadoScore resultado) {
        return new CalculoScoreResponse(
                resultado.score(),
                resultado.regrasAcionadas().stream().map(RegraAcionadaResponse::de).toList());
    }
}
