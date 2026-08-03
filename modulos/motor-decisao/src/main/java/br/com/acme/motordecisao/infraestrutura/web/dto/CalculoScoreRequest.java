package br.com.acme.motordecisao.infraestrutura.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * O motor recebe o resultado das listas ja apurado — ele nao consulta nada. Isso o mantem puro:
 * mesma entrada, mesmo score, testavel sem rede.
 */
public record CalculoScoreRequest(

        @NotBlank(message = "tipoTransacao e obrigatorio")
        @Size(max = 50, message = "tipoTransacao deve ter no maximo 50 caracteres")
        String tipoTransacao,

        @NotNull(message = "valorTransacao e obrigatorio")
        @Positive(message = "valorTransacao deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "valorTransacao admite no maximo 2 casas decimais")
        BigDecimal valorTransacao,

        @NotNull(message = "cpfEmListaPermissiva e obrigatorio")
        Boolean cpfEmListaPermissiva,

        @NotNull(message = "cpfEmListaRestritiva e obrigatorio")
        Boolean cpfEmListaRestritiva,

        @NotNull(message = "ipEmListaRestritiva e obrigatorio")
        Boolean ipEmListaRestritiva,

        @NotNull(message = "dispositivoEmListaRestritiva e obrigatorio")
        Boolean dispositivoEmListaRestritiva) {
}
