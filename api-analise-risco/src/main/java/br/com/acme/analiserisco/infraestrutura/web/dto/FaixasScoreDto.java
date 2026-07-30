package br.com.acme.analiserisco.infraestrutura.web.dto;

import br.com.acme.analiserisco.dominio.ClassificacaoRisco;
import br.com.acme.analiserisco.dominio.Decisao;
import br.com.acme.analiserisco.dominio.FaixaScore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public final class FaixasScoreDto {

    private FaixasScoreDto() {
    }

    public record Requisicao(
            @NotEmpty(message = "faixas e obrigatoria e nao pode ser vazia")
            @Valid
            List<FaixaRequisicao> faixas) {

        public List<FaixaScore> paraDominio() {
            return faixas.stream().map(FaixaRequisicao::paraDominio).toList();
        }
    }

    public record FaixaRequisicao(
            @NotNull(message = "classificacao e obrigatoria")
            ClassificacaoRisco classificacao,

            @Positive(message = "limiteSuperior deve ser maior que zero quando informado")
            Integer limiteSuperior,

            @NotNull(message = "decisao e obrigatoria")
            Decisao decisao) {

        public FaixaScore paraDominio() {
            return new FaixaScore(classificacao, limiteSuperior, decisao);
        }
    }

    public record Resposta(List<FaixaResposta> faixas) {

        public static Resposta de(List<FaixaScore> faixas) {
            return new Resposta(faixas.stream().map(FaixaResposta::de).toList());
        }
    }

    public record FaixaResposta(String classificacao, Integer limiteSuperior, String decisao) {

        public static FaixaResposta de(FaixaScore faixa) {
            return new FaixaResposta(faixa.classificacao().name(), faixa.limiteSuperior(),
                    faixa.decisao().name());
        }
    }
}
