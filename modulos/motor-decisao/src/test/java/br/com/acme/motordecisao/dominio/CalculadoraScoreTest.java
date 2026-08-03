package br.com.acme.motordecisao.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.acme.motordecisao.dominio.avaliacao.ContextoAvaliacao;
import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.OperadorLogico;
import br.com.acme.motordecisao.dominio.regra.Regra;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CalculadoraScoreTest {

    private static final String VALOR = "VALOR_TRANSACAO";

    private static Regra faixa(String chave, String limite, int pontos) {
        return Regra.deEscada(UUID.randomUUID(), chave, "PADRAO", chave, VALOR,
                limite == null ? null : new BigDecimal(limite), Acao.somar(pontos));
    }

    private static Regra condicional(String chave, Campo campo, Acao acao) {
        return Regra.condicional(UUID.randomUUID(), chave, "PADRAO", chave, null,
                List.of(Condicao.verdadeiro(campo)), acao);
    }

    /** As 7 regras padrao do enunciado. */
    private static List<Regra> regrasDoEnunciado() {
        return List.of(
                faixa("faixa_valor_1", "300.00", 200),
                faixa("faixa_valor_2", "5000.00", 300),
                faixa("faixa_valor_3", "20000.00", 400),
                faixa("faixa_valor_4", null, 500),
                condicional("cpf_lista_permissiva", Campo.CPF_EM_LISTA_PERMISSIVA, Acao.subtrair(200)),
                condicional("cpf_lista_restritiva", Campo.CPF_EM_LISTA_RESTRITIVA, Acao.somar(400)),
                Regra.condicional(UUID.randomUUID(), "ip_ou_dispositivo_lista_restritiva", "PADRAO",
                        "IP ou dispositivo em lista restritiva", OperadorLogico.OU,
                        List.of(Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA),
                                Condicao.verdadeiro(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA)),
                        Acao.somar(400)));
    }

    private static ConjuntoEfetivo conjuntoPadrao() {
        return ConjuntoEfetivo.compor("PIX", regrasDoEnunciado(), List.of());
    }

    private static ContextoAvaliacao contexto(String valor, boolean cpfPermissiva, boolean cpfRestritiva,
                                              boolean ipRestritiva, boolean dispositivoRestritiva) {
        return new ContextoAvaliacao("PIX", new BigDecimal(valor),
                cpfPermissiva, cpfRestritiva, ipRestritiva, dispositivoRestritiva);
    }

    @Nested
    @DisplayName("soma cumulativa")
    class SomaCumulativa {

        @Test
        @DisplayName("apenas a faixa de valor, sem sinal de listas")
        void somenteFaixaDeValor() {
            ResultadoScore resultado = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("150.00", false, false, false, false));

            assertThat(resultado.score()).isEqualTo(200);
            assertThat(resultado.regrasAcionadas()).hasSize(1);
        }

        @Test
        @DisplayName("faixa de valor somada a CPF em lista restritiva")
        void faixaMaisCpfRestritivo() {
            ResultadoScore resultado = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("1500.00", false, true, false, false));

            assertThat(resultado.score()).isEqualTo(700);
            assertThat(resultado.regrasAcionadas()).hasSize(2);
        }

        @Test
        @DisplayName("subtracao por CPF em lista permissiva")
        void subtracaoPorListaPermissiva() {
            ResultadoScore resultado = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("1500.00", true, false, false, false));

            assertThat(resultado.score()).isEqualTo(100);
        }

        @Test
        @DisplayName("CPF em ambas as listas: as duas regras aplicam")
        void cpfEmAmbasAsListas() {
            ResultadoScore resultado = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("1500.00", true, true, false, false));

            assertThat(resultado.score()).isEqualTo(500);
            assertThat(resultado.regrasAcionadas()).hasSize(3);
        }

        @Test
        @DisplayName("regra OU com IP e dispositivo em lista restritiva soma 400 uma unica vez")
        void regraOuSomaUmaVez() {
            ResultadoScore apenasIp = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("1500.00", false, false, true, false));
            ResultadoScore ambos = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("1500.00", false, false, true, true));

            assertThat(apenasIp.score()).isEqualTo(700);
            assertThat(ambos.score())
                    .as("IP e dispositivo restritivos devem somar 400 uma unica vez, nao 800")
                    .isEqualTo(700);
            assertThat(ambos.regrasAcionadas()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("piso do score")
    class PisoDoScore {

        @Test
        @DisplayName("soma negativa resulta no piso 1")
        void somaNegativaResultaNoPiso() {
            List<Regra> regras = List.of(
                    faixa("faixa_unica", null, 100),
                    condicional("penalidade", Campo.CPF_EM_LISTA_PERMISSIVA, Acao.subtrair(500)));
            ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", regras, List.of());

            ResultadoScore resultado = CalculadoraScore.calcular(conjunto,
                    contexto("150.00", true, false, false, false));

            assertThat(resultado.score()).isEqualTo(1);
        }

        @Test
        @DisplayName("soma exatamente zero resulta no piso 1")
        void somaZeroResultaNoPiso() {
            List<Regra> regras = List.of(
                    faixa("faixa_unica", null, 200),
                    condicional("desconto", Campo.CPF_EM_LISTA_PERMISSIVA, Acao.subtrair(200)));
            ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", regras, List.of());

            ResultadoScore resultado = CalculadoraScore.calcular(conjunto,
                    contexto("150.00", true, false, false, false));

            assertThat(resultado.score()).isEqualTo(1);
        }

        @Test
        @DisplayName("piso e aplicado uma unica vez ao final, nao a resultados intermediarios")
        void pisoAplicadoSomenteAoFinal() {
            List<Regra> regras = List.of(
                    faixa("faixa_unica", null, 100),
                    condicional("grande_desconto", Campo.CPF_EM_LISTA_PERMISSIVA, Acao.subtrair(500)),
                    condicional("penalidade_alta", Campo.CPF_EM_LISTA_RESTRITIVA, Acao.somar(450)));
            ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", regras, List.of());

            ResultadoScore resultado = CalculadoraScore.calcular(conjunto,
                    contexto("150.00", true, true, false, false));

            assertThat(resultado.score())
                    .as("100 - 500 + 450 = 50. Se o piso fosse aplicado a cada passo, o resultado seria 451")
                    .isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("cenarios do enunciado por tipo de transacao")
    class CenariosPorTipo {

        @Test
        @DisplayName("CARTAO herda faixa_valor_2 do padrao e redefine cpf_lista_permissiva")
        void cenarioCartao() {
            List<Regra> cartao = List.of(
                    Regra.deEscada(UUID.randomUUID(), "faixa_valor_1", "CARTAO", "cartao ate 300",
                            VALOR, new BigDecimal("300.00"), Acao.somar(350)),
                    Regra.condicional(UUID.randomUUID(), "cpf_lista_permissiva", "CARTAO",
                            "cartao permissiva", null,
                            List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_PERMISSIVA)), Acao.subtrair(400)));
            ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("CARTAO", regrasDoEnunciado(), cartao);

            ResultadoScore naFaixaRedefinida = CalculadoraScore.calcular(conjunto,
                    new ContextoAvaliacao("CARTAO", new BigDecimal("150.00"), false, false, false, false));
            ResultadoScore naFaixaHerdada = CalculadoraScore.calcular(conjunto,
                    new ContextoAvaliacao("CARTAO", new BigDecimal("1500.00"), false, false, false, false));

            assertThat(naFaixaRedefinida.score()).isEqualTo(350);
            assertThat(naFaixaHerdada.score()).isEqualTo(300);
        }

        @Test
        @DisplayName("conjunto sem nenhuma regra condicional acionada resulta apenas na faixa")
        void nenhumaCondicionalAcionada() {
            ResultadoScore resultado = CalculadoraScore.calcular(conjuntoPadrao(),
                    contexto("25000.00", false, false, false, false));

            assertThat(resultado.score()).isEqualTo(500);
            assertThat(resultado.regrasAcionadas()).hasSize(1);
        }
    }

    @Test
    @DisplayName("todas as regras acionadas sao reportadas para a trilha de auditoria")
    void reportaTodasAsRegrasAcionadas() {
        ResultadoScore resultado = CalculadoraScore.calcular(conjuntoPadrao(),
                contexto("25000.00", true, true, true, true));

        assertThat(resultado.regrasAcionadas())
                .extracting(Regra::chave)
                .containsExactlyInAnyOrder("faixa_valor_4", "cpf_lista_permissiva",
                        "cpf_lista_restritiva", "ip_ou_dispositivo_lista_restritiva");
        assertThat(resultado.score()).isEqualTo(1100);
    }
}
