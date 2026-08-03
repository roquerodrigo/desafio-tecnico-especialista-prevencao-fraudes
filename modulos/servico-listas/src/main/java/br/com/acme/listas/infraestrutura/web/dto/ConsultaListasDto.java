package br.com.acme.listas.infraestrutura.web.dto;

import br.com.acme.listas.dominio.Pertinencia;
import br.com.acme.listas.dominio.ResultadoConsulta;
import br.com.acme.listas.dominio.ResultadoVariavel;

public final class ConsultaListasDto {

    private ConsultaListasDto() {
    }

    /**
     * Verbo POST, e nao GET, de proposito: os identificadores consultados sao dados pessoais e nao
     * devem trafegar em query string, onde apareceriam em log de servidor e historico de proxy.
     */
    public record Requisicao(
            @jakarta.validation.constraints.Pattern(regexp = "\\d{11}",
                    message = "cpf deve conter exatamente 11 digitos")
            String cpf,
            String ip,
            String idDispositivo) {
    }

    public record Resposta(VariavelResposta cpf, VariavelResposta ip, VariavelResposta dispositivo) {

        public static Resposta de(ResultadoConsulta resultado) {
            return new Resposta(
                    VariavelResposta.de(resultado.cpf(), true),
                    VariavelResposta.de(resultado.ip(), false),
                    VariavelResposta.de(resultado.dispositivo(), false));
        }
    }

    /**
     * Pertinencia nula significa "nao consta". IP e dispositivo nunca expoem {@code permissiva},
     * porque apenas CPF admite lista permissiva.
     */
    public record VariavelResposta(String valor, PertinenciaResposta permissiva, PertinenciaResposta restritiva) {

        static VariavelResposta de(ResultadoVariavel resultado, boolean admitePermissiva) {
            if (resultado == null) {
                return null;
            }
            return new VariavelResposta(
                    resultado.valor(),
                    admitePermissiva ? PertinenciaResposta.de(resultado.permissiva()) : null,
                    PertinenciaResposta.de(resultado.restritiva()));
        }
    }

    public record PertinenciaResposta(String idLista, String situacao) {

        static PertinenciaResposta de(Pertinencia pertinencia) {
            return pertinencia == null
                    ? null
                    : new PertinenciaResposta(pertinencia.idLista(), pertinencia.situacao().name());
        }
    }
}
