package br.com.acme.motordecisao.dominio.composicao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.acme.motordecisao.dominio.CalculadoraScore;
import br.com.acme.motordecisao.dominio.ResultadoScore;
import br.com.acme.motordecisao.dominio.avaliacao.ContextoAvaliacao;
import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Escada;
import br.com.acme.motordecisao.dominio.regra.EscadaInvalidaException;
import br.com.acme.motordecisao.dominio.regra.Regra;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-023a — o modelo suporta multiplas escadas independentes, identificadas por nome, e as
 * invariantes valem <b>por escada</b>, nao globalmente.
 *
 * <p>O enunciado precisa de uma unica escada (faixa de valor), mas generalizar custou zero: a
 * escada ja e um campo da regra. Estes testes fixam esse comportamento — sem eles, uma
 * implementacao que tratasse "a escada" no singular passaria despercebida ate alguem cadastrar a
 * segunda.
 */
class MultiplasEscadasTest {

    private static final String ESCADA_VALOR = "VALOR_TRANSACAO";
    private static final String ESCADA_HORARIO = "HORARIO";

    private static Regra faixa(String escada, String chave, String limite, int pontos) {
        return Regra.deEscada(UUID.randomUUID(), chave, "PADRAO", chave + "@" + escada, escada,
                limite == null ? null : new BigDecimal(limite), Acao.somar(pontos));
    }

    private static ContextoAvaliacao contexto(String valor) {
        return new ContextoAvaliacao("PIX", new BigDecimal(valor), false, false, false, false);
    }

    @Test
    @DisplayName("duas escadas de nomes distintos sao independentes e ambas pontuam")
    void escadasIndependentesAmbasPontuam() {
        List<Regra> regras = List.of(
                faixa(ESCADA_VALOR, "faixa_valor_1", "300.00", 200),
                faixa(ESCADA_VALOR, "faixa_valor_2", null, 300),
                faixa(ESCADA_HORARIO, "faixa_horario_1", "12", 50),
                faixa(ESCADA_HORARIO, "faixa_horario_2", null, 90));

        ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", regras, List.of());

        assertThat(conjunto.escadas()).hasSize(2);

        ResultadoScore resultado = CalculadoraScore.calcular(conjunto, contexto("150.00"));

        assertThat(resultado.regrasAcionadas())
                .as("uma faixa de cada escada e acionada, nunca duas da mesma")
                .hasSize(2);
        assertThat(resultado.score())
                .as("200 da escada de valor + 90 da escada de horario, pois 150 > 12")
                .isEqualTo(290);
    }

    @Test
    @DisplayName("cada escada resolve sua propria faixa, sem interferencia da outra")
    void cadaEscadaResolveSuaFaixa() {
        List<Regra> regras = List.of(
                faixa(ESCADA_VALOR, "valor_baixo", "300.00", 200),
                faixa(ESCADA_VALOR, "valor_alto", null, 500),
                faixa(ESCADA_HORARIO, "horario_baixo", "1000.00", 10),
                faixa(ESCADA_HORARIO, "horario_alto", null, 70));

        ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", regras, List.of());

        ResultadoScore abaixoDeAmbas = CalculadoraScore.calcular(conjunto, contexto("150.00"));
        ResultadoScore entreAsDuas = CalculadoraScore.calcular(conjunto, contexto("500.00"));
        ResultadoScore acimaDeAmbas = CalculadoraScore.calcular(conjunto, contexto("5000.00"));

        assertThat(abaixoDeAmbas.score()).as("200 + 10").isEqualTo(210);
        assertThat(entreAsDuas.score()).as("500 + 10, pois 500 > 300 mas < 1000").isEqualTo(510);
        assertThat(acimaDeAmbas.score()).as("500 + 70").isEqualTo(570);
    }

    @Test
    @DisplayName("a invariante de faixa aberta vale por escada, nao globalmente")
    void invarianteDeFaixaAbertaValePorEscada() {
        List<Regra> umaEscadaCompletaOutraSem = List.of(
                faixa(ESCADA_VALOR, "faixa_valor_1", "300.00", 200),
                faixa(ESCADA_VALOR, "faixa_valor_2", null, 300),
                faixa(ESCADA_HORARIO, "faixa_horario_1", "12", 50));

        assertThatThrownBy(() -> ConjuntoEfetivo.compor("PIX", umaEscadaCompletaOutraSem, List.of()))
                .as("a escada de valor tem faixa aberta, mas a de horario nao — e isso basta para falhar")
                .isInstanceOf(EscadaInvalidaException.class)
                .hasMessageContaining(ESCADA_HORARIO);
    }

    @Test
    @DisplayName("limites iguais em escadas diferentes nao configuram duplicidade")
    void limitesIguaisEmEscadasDiferentesSaoValidos() {
        List<Regra> regras = List.of(
                faixa(ESCADA_VALOR, "valor_ate_300", "300.00", 200),
                faixa(ESCADA_VALOR, "valor_acima", null, 300),
                faixa(ESCADA_HORARIO, "horario_ate_300", "300.00", 50),
                faixa(ESCADA_HORARIO, "horario_acima", null, 90));

        ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", regras, List.of());

        assertThat(conjunto.escadas())
                .as("o limite 300 aparece nas duas escadas; a duplicidade so importa dentro de uma")
                .hasSize(2);
    }

    @Test
    @DisplayName("escada sem nenhuma regra no conjunto efetivo simplesmente nao pontua")
    void escadaAusenteNaoPontua() {
        List<Regra> apenasUmaEscada = List.of(
                faixa(ESCADA_VALOR, "faixa_valor_1", "300.00", 200),
                faixa(ESCADA_VALOR, "faixa_valor_2", null, 300));

        ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("PIX", apenasUmaEscada, List.of());
        ResultadoScore resultado = CalculadoraScore.calcular(conjunto, contexto("150.00"));

        assertThat(conjunto.escadas()).hasSize(1);
        assertThat(resultado.score()).isEqualTo(200);
        assertThat(resultado.regrasAcionadas()).hasSize(1);
    }

    @Test
    @DisplayName("a composicao por chave opera sobre a escada correta")
    void composicaoPorChaveRespeitaAEscada() {
        List<Regra> padrao = List.of(
                faixa(ESCADA_VALOR, "faixa_valor_1", "300.00", 200),
                faixa(ESCADA_VALOR, "faixa_valor_2", null, 300),
                faixa(ESCADA_HORARIO, "faixa_horario_1", "12", 50),
                faixa(ESCADA_HORARIO, "faixa_horario_2", null, 90));
        List<Regra> especificas = List.of(
                faixa(ESCADA_HORARIO, "faixa_horario_1", "12", 999));

        ConjuntoEfetivo conjunto = ConjuntoEfetivo.compor("CARTAO", padrao, especificas);

        assertThat(conjunto.origemDaChave("faixa_horario_1")).isEqualTo("CARTAO");
        assertThat(conjunto.origemDaChave("faixa_valor_1")).isEqualTo("PADRAO");
        assertThat(conjunto.escadas()).hasSize(2);
    }

    @Test
    @DisplayName("uma regra nao pode pertencer a uma escada e ser construida em outra")
    void regraDeOutraEscadaEhRejeitada() {
        Regra deValor = faixa(ESCADA_VALOR, "faixa_valor_1", "300.00", 200);
        Regra aberta = faixa(ESCADA_HORARIO, "faixa_horario_2", null, 90);

        assertThatThrownBy(() -> Escada.construir(ESCADA_HORARIO, List.of(deValor, aberta)))
                .isInstanceOf(EscadaInvalidaException.class)
                .hasMessageContaining("nao pertence a escada");
    }
}
