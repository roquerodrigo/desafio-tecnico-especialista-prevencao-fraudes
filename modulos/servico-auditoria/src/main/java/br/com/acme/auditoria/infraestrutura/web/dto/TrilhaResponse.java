package br.com.acme.auditoria.infraestrutura.web.dto;

import br.com.acme.auditoria.infraestrutura.persistencia.TrilhaDecisaoEntity;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TrilhaResponse(
        UUID id,
        String idCorrelacao,
        String cpf,
        String ip,
        String idDispositivo,
        String tipoTransacao,
        BigDecimal valorTransacao,
        int score,
        String classificacao,
        String decisao,
        boolean consultaListasDegradada,
        @JsonRawValue String regrasAcionadas,
        @JsonRawValue String resultadoListas,
        Instant ocorridoEm,
        Instant registradoEm) {

    public static TrilhaResponse de(TrilhaDecisaoEntity entidade) {
        return new TrilhaResponse(
                entidade.getId(),
                entidade.getIdCorrelacao(),
                entidade.getCpf(),
                entidade.getIp(),
                entidade.getIdDispositivo(),
                entidade.getTipoTransacao(),
                entidade.getValorTransacao(),
                entidade.getScore(),
                entidade.getClassificacao(),
                entidade.getDecisao(),
                entidade.isConsultaDegradada(),
                entidade.getRegrasAcionadas(),
                entidade.getResultadoListas(),
                entidade.getOcorridoEm(),
                entidade.getRegistradoEm());
    }
}
