package br.com.acme.motordecisao.dominio.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Operador;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContextoEExtratoresTest {

    private static final ContextoAvaliacao CONTEXTO = new ContextoAvaliacao(
            "PIX", new BigDecimal("1500.00"), true, false, true, false);

    @Nested
    @DisplayName("contexto de avaliacao")
    class Contexto {

        @Test
        @DisplayName("normaliza o tipo de transacao para maiusculas")
        void normalizaTipo() {
            ContextoAvaliacao contexto = new ContextoAvaliacao(
                    " cartao ", new BigDecimal("100"), false, false, false, false);

            assertThat(contexto.tipoTransacao()).isEqualTo("CARTAO");
        }

        @Test
        @DisplayName("rejeita valor nao positivo")
        void rejeitaValorNaoPositivo() {
            assertThatThrownBy(() -> new ContextoAvaliacao("PIX", BigDecimal.ZERO,
                    false, false, false, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positivo");
        }

        @Test
        @DisplayName("rejeita tipo e valor ausentes")
        void rejeitaCamposAusentes() {
            assertThatThrownBy(() -> new ContextoAvaliacao("  ", new BigDecimal("10"),
                    false, false, false, false))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ContextoAvaliacao("PIX", null,
                    false, false, false, false))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("extracao de campo")
    class Extracao {

        @Test
        @DisplayName("extrai cada campo do catalogo")
        void extraiTodosOsCampos() {
            assertThat(RegistroExtratoresCampo.extrair(Campo.VALOR_TRANSACAO, CONTEXTO))
                    .isEqualTo(new BigDecimal("1500.00"));
            assertThat(RegistroExtratoresCampo.extrair(Campo.TIPO_TRANSACAO, CONTEXTO))
                    .isEqualTo("PIX");
            assertThat(RegistroExtratoresCampo.extrair(Campo.CPF_EM_LISTA_PERMISSIVA, CONTEXTO))
                    .isEqualTo(true);
            assertThat(RegistroExtratoresCampo.extrair(Campo.CPF_EM_LISTA_RESTRITIVA, CONTEXTO))
                    .isEqualTo(false);
            assertThat(RegistroExtratoresCampo.extrair(Campo.IP_EM_LISTA_RESTRITIVA, CONTEXTO))
                    .isEqualTo(true);
            assertThat(RegistroExtratoresCampo.extrair(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA, CONTEXTO))
                    .isEqualTo(false);
        }

        @Test
        @DisplayName("coage o valor da condicao conforme o tipo do campo")
        void coercaoPorTipo() {
            assertThat(RegistroExtratoresCampo.coagir(Campo.VALOR_TRANSACAO, "300.50"))
                    .isEqualTo(new BigDecimal("300.50"));
            assertThat(RegistroExtratoresCampo.coagir(Campo.TIPO_TRANSACAO, " pix "))
                    .isEqualTo("PIX");
            assertThat(RegistroExtratoresCampo.coagir(Campo.CPF_EM_LISTA_PERMISSIVA, "true"))
                    .isEqualTo(true);
        }

        @Test
        @DisplayName("valor nao numerico em campo decimal devolve nulo, sem lancar excecao")
        void valorNaoNumericoDevolveNulo() {
            assertThat(RegistroExtratoresCampo.coagir(Campo.VALOR_TRANSACAO, "nao-e-numero")).isNull();
        }

        @Test
        @DisplayName("texto invalido em campo booleano coage para false, nunca lanca")
        void booleanoInvalidoCoageParaFalso() {
            assertThat(RegistroExtratoresCampo.coagir(Campo.CPF_EM_LISTA_PERMISSIVA, "5"))
                    .isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("registro de avaliadores")
    class Avaliadores {

        @Test
        @DisplayName("todos os operadores tem avaliador registrado")
        void todosOsOperadoresRegistrados() {
            for (Operador operador : Operador.values()) {
                assertThat(RegistroAvaliadores.para(operador)).isNotNull();
            }
        }

        @Test
        @DisplayName("igualdade de decimal compara valor, nao escala")
        void igualdadeDecimalPorValor() {
            AvaliadorCondicao igual = RegistroAvaliadores.para(Operador.IGUAL);

            assertThat(igual.avaliar(new BigDecimal("300.00"), new BigDecimal("300")))
                    .as("300.00 e 300 representam o mesmo valor; equals() de BigDecimal diria false")
                    .isTrue();
        }

        @Test
        @DisplayName("comparacoes ordinais sobre decimal")
        void comparacoesOrdinais() {
            BigDecimal mil = new BigDecimal("1000.00");
            BigDecimal quinhentos = new BigDecimal("500.00");

            assertThat(RegistroAvaliadores.para(Operador.MAIOR_QUE).avaliar(mil, quinhentos)).isTrue();
            assertThat(RegistroAvaliadores.para(Operador.MAIOR_OU_IGUAL).avaliar(mil, mil)).isTrue();
            assertThat(RegistroAvaliadores.para(Operador.MENOR_QUE).avaliar(quinhentos, mil)).isTrue();
            assertThat(RegistroAvaliadores.para(Operador.MENOR_OU_IGUAL).avaliar(mil, mil)).isTrue();
            assertThat(RegistroAvaliadores.para(Operador.DIFERENTE).avaliar(mil, quinhentos)).isTrue();
        }

        @Test
        @DisplayName("tipos incompativeis nao acionam, em vez de lancar excecao")
        void tiposIncompativeisNaoAcionam() {
            AvaliadorCondicao maiorQue = RegistroAvaliadores.para(Operador.MAIOR_QUE);

            assertThat(maiorQue.avaliar(new BigDecimal("10"), "texto")).isFalse();
            assertThat(maiorQue.avaliar(null, new BigDecimal("10"))).isFalse();
            assertThat(maiorQue.avaliar(new BigDecimal("10"), null)).isFalse();
        }

        @Test
        @DisplayName("igualdade cai em equals quando a comparacao ordinal nao se aplica")
        void igualdadeComFallbackParaEquals() {
            AvaliadorCondicao igual = RegistroAvaliadores.para(Operador.IGUAL);

            assertThat(igual.avaliar(true, true)).isTrue();
            assertThat(igual.avaliar(true, false)).isFalse();
            assertThat(igual.avaliar(null, null)).isTrue();
        }
    }
}
