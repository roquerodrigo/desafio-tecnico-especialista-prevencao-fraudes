package br.com.acme.motordecisao.dominio.regra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EscadaTest {

    private static final String VALOR = "VALOR_TRANSACAO";

    private static Regra faixa(String chave, String limite, int pontos) {
        return Regra.deEscada(UUID.randomUUID(), chave, "PADRAO", "faixa " + chave, VALOR,
                limite == null ? null : new BigDecimal(limite), Acao.somar(pontos));
    }

    private static Escada escadaDoEnunciado() {
        return Escada.construir(VALOR, List.of(
                faixa("faixa_valor_1", "300.00", 200),
                faixa("faixa_valor_2", "5000.00", 300),
                faixa("faixa_valor_3", "20000.00", 400),
                faixa("faixa_valor_4", null, 500)));
    }

    @Nested
    @DisplayName("resolucao da faixa")
    class Resolucao {

        @ParameterizedTest(name = "valor {0} resolve para a faixa de {1} pontos")
        @CsvSource({
                "0.01, 200",
                "150.00, 200",
                "300.00, 200",
                "300.01, 300",
                "1500.00, 300",
                "5000.00, 300",
                "5000.01, 400",
                "20000.00, 400",
                "20000.01, 500",
                "999999.99, 500"
        })
        void resolveExatamenteUmaFaixaPorValor(String valor, int pontosEsperados) {
            Regra resolvida = escadaDoEnunciado().resolver(new BigDecimal(valor));

            assertThat(resolvida.acao().pontos()).isEqualTo(pontosEsperados);
        }

        @Test
        @DisplayName("valor exatamente no limite pertence a faixa que o declara, nao a seguinte")
        void limiteEhInclusivo() {
            Regra noLimite = escadaDoEnunciado().resolver(new BigDecimal("300.00"));
            Regra acimaDoLimite = escadaDoEnunciado().resolver(new BigDecimal("300.01"));

            assertThat(noLimite.chave()).isEqualTo("faixa_valor_1");
            assertThat(acimaDoLimite.chave()).isEqualTo("faixa_valor_2");
        }

        @Test
        @DisplayName("escala decimal diferente nao altera a faixa resolvida")
        void escalaNaoAfetaResolucao() {
            Escada escada = escadaDoEnunciado();

            assertThat(escada.resolver(new BigDecimal("300")).chave()).isEqualTo("faixa_valor_1");
            assertThat(escada.resolver(new BigDecimal("300.00")).chave()).isEqualTo("faixa_valor_1");
            assertThat(escada.resolver(new BigDecimal("300.0000")).chave()).isEqualTo("faixa_valor_1");
        }

        @Test
        @DisplayName("valor acima do maior teto cai na faixa sem teto")
        void valorAcimaDeTodosOsTetos() {
            Regra resolvida = escadaDoEnunciado().resolver(new BigDecimal("1000000.00"));

            assertThat(resolvida.chave()).isEqualTo("faixa_valor_4");
            assertThat(resolvida.semTeto()).isTrue();
        }
    }

    @Nested
    @DisplayName("invariantes verificadas na carga")
    class Invariantes {

        @Test
        @DisplayName("rejeita escada sem nenhuma faixa aberta")
        void exigeFaixaSemTeto() {
            List<Regra> semFaixaAberta = List.of(
                    faixa("faixa_valor_1", "300.00", 200),
                    faixa("faixa_valor_2", "5000.00", 300));

            assertThatThrownBy(() -> Escada.construir(VALOR, semFaixaAberta))
                    .isInstanceOf(EscadaInvalidaException.class)
                    .hasMessageContaining("exige exatamente uma faixa sem limite superior");
        }

        @Test
        @DisplayName("rejeita escada com mais de uma faixa aberta")
        void rejeitaDuasFaixasSemTeto() {
            List<Regra> duasAbertas = List.of(
                    faixa("faixa_valor_1", "300.00", 200),
                    faixa("faixa_valor_4", null, 500),
                    faixa("faixa_valor_5", null, 600));

            assertThatThrownBy(() -> Escada.construir(VALOR, duasAbertas))
                    .isInstanceOf(EscadaInvalidaException.class)
                    .hasMessageContaining("mais de uma faixa sem limite superior");
        }

        @Test
        @DisplayName("rejeita limites superiores duplicados")
        void rejeitaLimiteDuplicado() {
            List<Regra> comDuplicata = List.of(
                    faixa("faixa_valor_1", "300.00", 200),
                    faixa("faixa_valor_outra", "300.00", 250),
                    faixa("faixa_valor_4", null, 500));

            assertThatThrownBy(() -> Escada.construir(VALOR, comDuplicata))
                    .isInstanceOf(EscadaInvalidaException.class)
                    .hasMessageContaining("limite superior duplicado");
        }

        @Test
        @DisplayName("detecta duplicata mesmo com escalas decimais diferentes")
        void duplicataComEscalaDiferente() {
            List<Regra> comDuplicata = List.of(
                    faixa("faixa_a", "300", 200),
                    faixa("faixa_b", "300.00", 250),
                    faixa("faixa_aberta", null, 500));

            assertThatThrownBy(() -> Escada.construir(VALOR, comDuplicata))
                    .isInstanceOf(EscadaInvalidaException.class)
                    .hasMessageContaining("limite superior duplicado");
        }

        @Test
        @DisplayName("rejeita escada vazia")
        void rejeitaEscadaVazia() {
            assertThatThrownBy(() -> Escada.construir(VALOR, List.of()))
                    .isInstanceOf(EscadaInvalidaException.class)
                    .hasMessageContaining("nao possui nenhuma faixa");
        }

        @Test
        @DisplayName("rejeita regra que pertence a outra escada")
        void rejeitaRegraDeOutraEscada() {
            Regra deOutraEscada = Regra.deEscada(UUID.randomUUID(), "chave", "PADRAO", "outra",
                    "HORARIO", new BigDecimal("18"), Acao.somar(100));

            assertThatThrownBy(() -> Escada.construir(VALOR,
                    List.of(deOutraEscada, faixa("aberta", null, 500))))
                    .isInstanceOf(EscadaInvalidaException.class)
                    .hasMessageContaining("nao pertence a escada");
        }

        @Test
        @DisplayName("limite superior nao positivo e rejeitado na construcao da regra")
        void rejeitaLimiteNaoPositivo() {
            assertThatThrownBy(() -> faixa("faixa_zero", "0.00", 200))
                    .isInstanceOf(NaturezaRegraInvalidaException.class)
                    .hasMessageContaining("Limite superior deve ser positivo");
        }
    }

    @Test
    @DisplayName("quantidade de faixas inclui a faixa sem teto")
    void quantidadeDeFaixas() {
        assertThat(escadaDoEnunciado().quantidadeFaixas()).isEqualTo(4);
        assertThat(escadaDoEnunciado().faixas()).hasSize(4);
        assertThat(escadaDoEnunciado().nome()).isEqualTo(VALOR);
    }
}
