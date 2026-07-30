package br.com.acme.geradortrafego.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Percentil e ambiguo — existem varias definicoes, e bibliotecas diferentes devolvem valores
 * diferentes para a mesma amostra. Estes testes fixam o metodo <b>nearest-rank</b>, que e o
 * declarado na documentacao: posicao {@code ceil(p/100 × n)}, contada a partir de 1.
 */
class CalculadoraPercentilTest {

    /** 1..100 ordenados: o percentil p e exatamente o valor p. */
    private static final List<Long> UM_A_CEM =
            LongStream.rangeClosed(1, 100).boxed().toList();

    @ParameterizedTest(name = "p{0} de 1..100 e {1}")
    @CsvSource({
            "50, 50",
            "95, 95",
            "99, 99",
            "100, 100",
            "1, 1"
    })
    @DisplayName("nearest-rank sobre amostra conhecida")
    void nearestRankSobreAmostraConhecida(int percentil, long esperado) {
        assertThat(CalculadoraPercentil.percentil(UM_A_CEM, percentil)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("devolve sempre uma medicao real, nunca um valor interpolado")
    void naoInterpola() {
        List<Long> amostras = List.of(10L, 20L, 30L);

        assertThat(CalculadoraPercentil.percentil(amostras, 50))
                .as("com 3 amostras, p50 e ceil(1.5)=2 -> o segundo elemento")
                .isEqualTo(20L);
        assertThat(CalculadoraPercentil.percentil(amostras, 95)).isEqualTo(30L);
    }

    @Test
    @DisplayName("amostra unica devolve o proprio valor em qualquer percentil")
    void amostraUnica() {
        List<Long> unica = List.of(42L);

        assertThat(CalculadoraPercentil.percentil(unica, 50)).isEqualTo(42L);
        assertThat(CalculadoraPercentil.percentil(unica, 99)).isEqualTo(42L);
    }

    @Test
    @DisplayName("amostra vazia devolve zero em vez de lancar")
    void amostraVazia() {
        assertThat(CalculadoraPercentil.percentil(List.of(), 95)).isZero();
        assertThat(CalculadoraPercentil.media(List.of())).isZero();
    }

    @Test
    @DisplayName("percentil zero devolve o menor valor, sem estourar indice")
    void percentilZero() {
        assertThat(CalculadoraPercentil.percentil(UM_A_CEM, 0)).isEqualTo(1L);
    }

    @Test
    @DisplayName("rejeita percentil fora do intervalo valido")
    void rejeitaPercentilInvalido() {
        assertThatThrownBy(() -> CalculadoraPercentil.percentil(UM_A_CEM, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 0 e 100");
        assertThatThrownBy(() -> CalculadoraPercentil.percentil(UM_A_CEM, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("p95 e sensivel a cauda — e por isso que ele importa, e nao a media")
    void p95CapturaCauda() {
        List<Long> comCauda = new java.util.ArrayList<>(LongStream.range(0, 95).map(i -> 10).boxed().toList());
        comCauda.addAll(LongStream.range(0, 5).map(i -> 900).boxed().toList());

        assertThat(CalculadoraPercentil.media(comCauda))
                .as("a media dilui a cauda")
                .isLessThan(60.0);
        assertThat(CalculadoraPercentil.percentil(comCauda, 95))
                .as("o p95 expoe a cauda que a media esconde")
                .isEqualTo(10L);
        assertThat(CalculadoraPercentil.percentil(comCauda, 99)).isEqualTo(900L);
    }

    @Test
    @DisplayName("media aritmetica simples")
    void media() {
        assertThat(CalculadoraPercentil.media(List.of(10L, 20L, 30L))).isEqualTo(20.0);
    }
}
