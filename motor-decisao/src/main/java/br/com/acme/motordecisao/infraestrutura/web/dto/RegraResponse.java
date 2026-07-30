package br.com.acme.motordecisao.infraestrutura.web.dto;

import br.com.acme.motordecisao.dominio.regra.Regra;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegraResponse(
        UUID id,
        String chave,
        String tipoTransacao,
        String origem,
        String descricao,
        String escada,
        BigDecimal limiteSuperior,
        String operadorLogico,
        List<CondicaoResponse> condicoes,
        AcaoResponse acao) {

    public static RegraResponse de(Regra regra) {
        return de(regra, null);
    }

    /**
     * @param origem de qual conjunto a regra veio na composicao ({@code PADRAO} ou o tipo).
     *               Torna a chave de sobreposicao observavel pela API.
     */
    public static RegraResponse de(Regra regra, String origem) {
        return new RegraResponse(
                regra.id(),
                regra.chave(),
                regra.tipoTransacao(),
                origem,
                regra.descricao(),
                regra.escada(),
                regra.limiteSuperior(),
                regra.ehEscada() ? null : regra.operadorLogico().name(),
                regra.condicoes().stream().map(CondicaoResponse::de).toList(),
                new AcaoResponse(regra.acao().tipo().name(), regra.acao().pontos()));
    }

    public record CondicaoResponse(String campo, String operador, String valor) {

        public static CondicaoResponse de(br.com.acme.motordecisao.dominio.regra.Condicao condicao) {
            return new CondicaoResponse(condicao.campo().name(), condicao.operador().name(), condicao.valor());
        }
    }

    public record AcaoResponse(String tipo, int pontos) {
    }
}
