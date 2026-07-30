package br.com.acme.motordecisao.infraestrutura.web.dto;

import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.Operador;
import br.com.acme.motordecisao.dominio.regra.OperadorLogico;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.dominio.regra.TipoAcao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record RegraRequest(

        @Size(max = 100, message = "chave deve ter no maximo 100 caracteres")
        String chave,

        @NotBlank(message = "tipoTransacao e obrigatorio")
        @Size(max = 50, message = "tipoTransacao deve ter no maximo 50 caracteres")
        String tipoTransacao,

        @NotBlank(message = "descricao e obrigatoria")
        @Size(max = 255, message = "descricao deve ter no maximo 255 caracteres")
        String descricao,

        @Size(max = 100, message = "escada deve ter no maximo 100 caracteres")
        String escada,

        @Positive(message = "limiteSuperior deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "limiteSuperior admite no maximo 2 casas decimais")
        BigDecimal limiteSuperior,

        OperadorLogico operadorLogico,

        @Valid
        List<CondicaoRequest> condicoes,

        @NotNull(message = "acao e obrigatoria")
        @Valid
        AcaoRequest acao) {

    /**
     * Repassa tudo o que veio na requisicao e deixa a invariante de {@link Regra} decidir.
     *
     * <p>Escolher aqui entre escada e condicional — ignorando o que nao se encaixa — faria o DTO
     * descartar silenciosamente campos incoerentes: uma requisicao com {@code escada} e
     * {@code condicoes} seria aceita e as condicoes simplesmente desapareceriam, sem que o
     * cadastrante soubesse. Passando tudo, a regra hibrida e rejeitada com 422.
     */
    public Regra paraDominio(UUID id) {
        return Regra.reconstituir(
                id,
                chave,
                tipoTransacao,
                descricao,
                escada,
                limiteSuperior,
                operadorLogico,
                condicoes == null ? List.of() : condicoes.stream().map(CondicaoRequest::paraDominio).toList(),
                acao.paraDominio());
    }

    public record CondicaoRequest(
            @NotNull(message = "campo da condicao e obrigatorio") Campo campo,
            @NotNull(message = "operador da condicao e obrigatorio") Operador operador,
            @NotBlank(message = "valor da condicao e obrigatorio") String valor) {

        public Condicao paraDominio() {
            return new Condicao(campo, operador, valor);
        }
    }

    public record AcaoRequest(
            @NotNull(message = "tipo da acao e obrigatorio") TipoAcao tipo,
            @Positive(message = "pontos devem ser maiores que zero") int pontos) {

        public Acao paraDominio() {
            return new Acao(tipo, pontos);
        }
    }
}
