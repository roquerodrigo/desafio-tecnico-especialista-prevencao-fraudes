package br.com.acme.motordecisao.infraestrutura.web.dto;

import br.com.acme.motordecisao.dominio.regra.Regra;
import java.util.UUID;

public record RegraAcionadaResponse(UUID id, String chave, String descricao, String acao, int pontos) {

    public static RegraAcionadaResponse de(Regra regra) {
        return new RegraAcionadaResponse(
                regra.id(),
                regra.chave(),
                regra.descricao(),
                regra.acao().tipo().name(),
                regra.acao().pontos());
    }
}
