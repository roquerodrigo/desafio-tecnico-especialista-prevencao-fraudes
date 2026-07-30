package br.com.acme.analiserisco.infraestrutura.web.dto;

import br.com.acme.analiserisco.dominio.Transacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record AnaliseRiscoRequest(

        @NotBlank(message = "cpf e obrigatorio")
        @jakarta.validation.constraints.Pattern(regexp = "\\d{11}", message = "cpf deve conter exatamente 11 digitos")
        String cpf,

        @NotBlank(message = "ip e obrigatorio")
        String ip,

        @NotNull(message = "idDispositivo e obrigatorio")
        UUID idDispositivo,

        @NotBlank(message = "tipoTransacao e obrigatorio")
        @Size(max = 50, message = "tipoTransacao deve ter no maximo 50 caracteres")
        String tipoTransacao,

        @NotNull(message = "valorTransacao e obrigatorio")
        BigDecimal valorTransacao) {

    public Transacao paraDominio() {
        return new Transacao(cpf, ip, idDispositivo, tipoTransacao, valorTransacao);
    }
}
