package br.com.acme.motordecisao.dominio.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.Operador;
import br.com.acme.motordecisao.dominio.regra.OperadorLogico;
import br.com.acme.motordecisao.dominio.regra.Regra;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AvaliadorRegraTest {

    private static ContextoAvaliacao contexto(boolean cpfPermissiva, boolean cpfRestritiva,
                                              boolean ipRestritiva, boolean dispositivoRestritiva) {
        return new ContextoAvaliacao("PIX", new BigDecimal("1500.00"),
                cpfPermissiva, cpfRestritiva, ipRestritiva, dispositivoRestritiva);
    }

    private static Regra comCondicoes(OperadorLogico operador, List<Condicao> condicoes) {
        return Regra.condicional(UUID.randomUUID(), "chave", "PIX", "regra de teste",
                operador, condicoes, Acao.somar(400));
    }

    @Nested
    @DisplayName("operador logico E")
    class OperadorE {

        @Test
        @DisplayName("aciona quando todas as condicoes sao satisfeitas")
        void todasSatisfeitas() {
            Regra regra = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA),
                    Condicao.verdadeiro(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA)));

            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, true, true))).isTrue();
        }

        @Test
        @DisplayName("nao aciona quando uma condicao falha")
        void umaFalha() {
            Regra regra = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA),
                    Condicao.verdadeiro(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA)));

            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, true, false))).isFalse();
        }

        @Test
        @DisplayName("operador logico omitido assume E")
        void operadorOmitidoAssumeE() {
            Regra regra = comCondicoes(null, List.of(
                    Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA),
                    Condicao.verdadeiro(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA)));

            assertThat(regra.operadorLogico()).isEqualTo(OperadorLogico.E);
            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, true, false))).isFalse();
            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, true, true))).isTrue();
        }
    }

    @Nested
    @DisplayName("operador logico OU")
    class OperadorOu {

        private final Regra regraOu = comCondicoes(OperadorLogico.OU, List.of(
                Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA),
                Condicao.verdadeiro(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA)));

        @Test
        @DisplayName("aciona com apenas uma condicao satisfeita")
        void umaSatisfeita() {
            assertThat(AvaliadorRegra.acionada(regraOu, contexto(false, false, true, false))).isTrue();
            assertThat(AvaliadorRegra.acionada(regraOu, contexto(false, false, false, true))).isTrue();
        }

        @Test
        @DisplayName("com ambas satisfeitas, aciona uma unica vez — a avaliacao devolve um booleano, nao uma contagem")
        void ambasSatisfeitasAcionamUmaVez() {
            boolean acionada = AvaliadorRegra.acionada(regraOu, contexto(false, false, true, true));

            assertThat(acionada).isTrue();
        }

        @Test
        @DisplayName("nao aciona quando nenhuma condicao e satisfeita")
        void nenhumaSatisfeita() {
            assertThat(AvaliadorRegra.acionada(regraOu, contexto(false, false, false, false))).isFalse();
        }
    }

    @Nested
    @DisplayName("comparacoes por tipo de campo")
    class Comparacoes {

        @Test
        @DisplayName("compara valor monetario por valor numerico, nao por escala decimal")
        void comparacaoMonetariaIgnoraEscala() {
            Regra regra = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.VALOR_TRANSACAO, Operador.IGUAL, "1500")));

            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, false, false))).isTrue();
        }

        @Test
        @DisplayName("compara valor monetario com operadores ordinais")
        void comparacaoOrdinalMonetaria() {
            Regra maiorQue = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.VALOR_TRANSACAO, Operador.MAIOR_QUE, "1000.00")));
            Regra menorOuIgual = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.VALOR_TRANSACAO, Operador.MENOR_OU_IGUAL, "1500.00")));

            assertThat(AvaliadorRegra.acionada(maiorQue, contexto(false, false, false, false))).isTrue();
            assertThat(AvaliadorRegra.acionada(menorOuIgual, contexto(false, false, false, false))).isTrue();
        }

        @Test
        @DisplayName("compara tipo de transacao normalizando a caixa")
        void comparacaoDeTextoNormalizaCaixa() {
            Regra regra = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.TIPO_TRANSACAO, Operador.IGUAL, "pix")));

            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, false, false))).isTrue();
        }

        @Test
        @DisplayName("operador DIFERENTE funciona sobre texto")
        void operadorDiferente() {
            Regra regra = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.TIPO_TRANSACAO, Operador.DIFERENTE, "TED")));

            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, false, false))).isTrue();
        }

        @Test
        @DisplayName("comparacao ordinal sobre booleano nunca aciona — Boolean e Comparable em Java, e true>false acionaria a regra sem sentido")
        void ordinalSobreBooleanoNaoAciona() {
            Regra semanticamenteInutil = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.CPF_EM_LISTA_PERMISSIVA, Operador.MAIOR_QUE, "5")));

            assertThat(AvaliadorRegra.acionada(semanticamenteInutil, contexto(true, false, false, false)))
                    .as("sem o bloqueio explicito, Boolean.TRUE.compareTo(FALSE)=1 faria a regra acionar "
                            + "para todo CPF permissivo — falso positivo silencioso")
                    .isFalse();
        }

        @Test
        @DisplayName("igualdade sobre booleano continua funcionando normalmente")
        void igualdadeSobreBooleanoFunciona() {
            Regra verdadeiro = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.CPF_EM_LISTA_RESTRITIVA, Operador.IGUAL, "true")));
            Regra falso = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.CPF_EM_LISTA_RESTRITIVA, Operador.IGUAL, "false")));

            assertThat(AvaliadorRegra.acionada(verdadeiro, contexto(false, true, false, false))).isTrue();
            assertThat(AvaliadorRegra.acionada(falso, contexto(false, true, false, false))).isFalse();
            assertThat(AvaliadorRegra.acionada(falso, contexto(false, false, false, false))).isTrue();
        }

        @Test
        @DisplayName("DIFERENTE sobre booleano continua funcionando")
        void diferenteSobreBooleanoFunciona() {
            Regra regra = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.CPF_EM_LISTA_RESTRITIVA, Operador.DIFERENTE, "false")));

            assertThat(AvaliadorRegra.acionada(regra, contexto(false, true, false, false))).isTrue();
            assertThat(AvaliadorRegra.acionada(regra, contexto(false, false, false, false))).isFalse();
        }

        @Test
        @DisplayName("valor nao numerico em campo decimal nao aciona, em vez de lancar excecao")
        void valorNaoNumericoNaoAciona() {
            Regra invalida = comCondicoes(OperadorLogico.E, List.of(
                    Condicao.de(Campo.VALOR_TRANSACAO, Operador.MAIOR_QUE, "abc")));

            assertThat(AvaliadorRegra.acionada(invalida, contexto(false, false, false, false))).isFalse();
        }
    }

    @Test
    @DisplayName("regra de escada nao pode ser avaliada por condicoes")
    void escadaNaoEhAvaliadaPorCondicoes() {
        Regra escada = Regra.deEscada(UUID.randomUUID(), "faixa_valor_1", "PIX", "faixa",
                "VALOR_TRANSACAO", new BigDecimal("300.00"), Acao.somar(200));

        assertThatThrownBy(() -> AvaliadorRegra.acionada(escada, contexto(false, false, false, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nao e avaliada por condicoes");
    }
}
