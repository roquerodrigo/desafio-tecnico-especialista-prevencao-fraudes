package br.com.acme.analiserisco.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TabelaFaixasTest {

    private static TabelaFaixas tabelaPadrao() {
        return TabelaFaixas.construir(List.of(
                new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                new FaixaScore(ClassificacaoRisco.MEDIO, 699, Decisao.APROVADA),
                new FaixaScore(ClassificacaoRisco.ALTO, null, Decisao.NEGADA)));
    }

    @Nested
    @DisplayName("classificacao do score")
    class Classificacao {

        @ParameterizedTest(name = "score {0} classifica como {1} e decide {2}")
        @CsvSource({
                "1, BAIXO, APROVADA",
                "200, BAIXO, APROVADA",
                "399, BAIXO, APROVADA",
                "400, MEDIO, APROVADA",
                "550, MEDIO, APROVADA",
                "699, MEDIO, APROVADA",
                "700, ALTO, NEGADA",
                "1500, ALTO, NEGADA"
        })
        void classificaConformeOsIntervalosDoEnunciado(int score, String classificacao, String decisao) {
            FaixaScore faixa = tabelaPadrao().classificar(score);

            assertThat(faixa.classificacao().name()).isEqualTo(classificacao);
            assertThat(faixa.decisao().name()).isEqualTo(decisao);
        }

        @Test
        @DisplayName("os limites 399/400 e 699/700 do enunciado sao respeitados exatamente")
        void limitesSaoInclusivos() {
            TabelaFaixas tabela = tabelaPadrao();

            assertThat(tabela.classificar(399).classificacao()).isEqualTo(ClassificacaoRisco.BAIXO);
            assertThat(tabela.classificar(400).classificacao()).isEqualTo(ClassificacaoRisco.MEDIO);
            assertThat(tabela.classificar(699).classificacao()).isEqualTo(ClassificacaoRisco.MEDIO);
            assertThat(tabela.classificar(700).classificacao()).isEqualTo(ClassificacaoRisco.ALTO);
        }

        @Test
        @DisplayName("score muito acima do maior teto cai na faixa sem teto")
        void scoreAcimaDeTodosOsTetos() {
            assertThat(tabelaPadrao().classificar(999_999).classificacao()).isEqualTo(ClassificacaoRisco.ALTO);
        }
    }

    @Nested
    @DisplayName("politica de decisao como dado")
    class PoliticaComoDado {

        @Test
        @DisplayName("alterar a decisao da faixa media muda o resultado sem tocar em codigo")
        void alterarPoliticaDeRiscoMedio() {
            TabelaFaixas restritiva = TabelaFaixas.construir(List.of(
                    new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.MEDIO, 699, Decisao.NEGADA),
                    new FaixaScore(ClassificacaoRisco.ALTO, null, Decisao.NEGADA)));

            assertThat(tabelaPadrao().classificar(500).decisao()).isEqualTo(Decisao.APROVADA);
            assertThat(restritiva.classificar(500).decisao()).isEqualTo(Decisao.NEGADA);
        }
    }

    @Nested
    @DisplayName("invariantes verificadas na carga")
    class Invariantes {

        @Test
        @DisplayName("rejeita tabela sem faixa aberta, que deixaria scores altos sem classificacao")
        void exigeFaixaSemTeto() {
            List<FaixaScore> semFaixaAberta = List.of(
                    new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.MEDIO, 699, Decisao.APROVADA));

            assertThatThrownBy(() -> TabelaFaixas.construir(semFaixaAberta))
                    .isInstanceOf(TabelaFaixasInvalidaException.class)
                    .hasMessageContaining("foram encontradas 0");
        }

        @Test
        @DisplayName("rejeita duas faixas abertas")
        void rejeitaDuasFaixasSemTeto() {
            List<FaixaScore> duasAbertas = List.of(
                    new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.MEDIO, null, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.ALTO, null, Decisao.NEGADA));

            assertThatThrownBy(() -> TabelaFaixas.construir(duasAbertas))
                    .isInstanceOf(TabelaFaixasInvalidaException.class)
                    .hasMessageContaining("foram encontradas 2");
        }

        @Test
        @DisplayName("rejeita limites superiores duplicados")
        void rejeitaLimiteDuplicado() {
            List<FaixaScore> comDuplicata = List.of(
                    new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.MEDIO, 399, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.ALTO, null, Decisao.NEGADA));

            assertThatThrownBy(() -> TabelaFaixas.construir(comDuplicata))
                    .isInstanceOf(TabelaFaixasInvalidaException.class)
                    .hasMessageContaining("Limite superior duplicado");
        }

        @Test
        @DisplayName("rejeita classificacao duplicada")
        void rejeitaClassificacaoDuplicada() {
            List<FaixaScore> comDuplicata = List.of(
                    new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.BAIXO, 699, Decisao.APROVADA),
                    new FaixaScore(ClassificacaoRisco.ALTO, null, Decisao.NEGADA));

            assertThatThrownBy(() -> TabelaFaixas.construir(comDuplicata))
                    .isInstanceOf(TabelaFaixasInvalidaException.class)
                    .hasMessageContaining("Classificacao duplicada");
        }

        @Test
        @DisplayName("rejeita limite superior nao positivo")
        void rejeitaLimiteNaoPositivo() {
            assertThatThrownBy(() -> new FaixaScore(ClassificacaoRisco.BAIXO, 0, Decisao.APROVADA))
                    .isInstanceOf(TabelaFaixasInvalidaException.class)
                    .hasMessageContaining("deve ser positivo");
        }

        @Test
        @DisplayName("rejeita tabela vazia")
        void rejeitaTabelaVazia() {
            assertThatThrownBy(() -> TabelaFaixas.construir(List.of()))
                    .isInstanceOf(TabelaFaixasInvalidaException.class)
                    .hasMessageContaining("ao menos uma faixa");
        }
    }

    @Test
    @DisplayName("faixas retornam ordenadas por limite, com a faixa sem teto ao final")
    void faixasOrdenadas() {
        List<FaixaScore> faixas = tabelaPadrao().faixas();

        assertThat(faixas).hasSize(3);
        assertThat(faixas.get(0).classificacao()).isEqualTo(ClassificacaoRisco.BAIXO);
        assertThat(faixas.get(1).classificacao()).isEqualTo(ClassificacaoRisco.MEDIO);
        assertThat(faixas.get(2).semTeto()).isTrue();
    }
}
