package br.com.acme.motordecisao.dominio.regra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegraTest {

    private static final UUID ID = UUID.randomUUID();

    @Nested
    @DisplayName("invariante de natureza")
    class Natureza {

        @Test
        @DisplayName("regra de escada e valida sem condicoes")
        void escadaValida() {
            Regra regra = Regra.deEscada(ID, "faixa_valor_1", "PIX", "faixa", "VALOR_TRANSACAO",
                    new BigDecimal("300.00"), Acao.somar(200));

            assertThat(regra.ehEscada()).isTrue();
            assertThat(regra.condicoes()).isEmpty();
            assertThat(regra.semTeto()).isFalse();
        }

        @Test
        @DisplayName("regra de escada sem limite superior e a faixa aberta")
        void escadaSemTeto() {
            Regra regra = Regra.deEscada(ID, "faixa_valor_4", "PIX", "acima de tudo",
                    "VALOR_TRANSACAO", null, Acao.somar(500));

            assertThat(regra.semTeto()).isTrue();
        }

        @Test
        @DisplayName("regra condicional e valida com condicoes")
        void condicionalValida() {
            Regra regra = Regra.condicional(ID, "chave", "PIX", "condicional", OperadorLogico.OU,
                    List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(400));

            assertThat(regra.ehEscada()).isFalse();
            assertThat(regra.condicoes()).hasSize(1);
        }

        @Test
        @DisplayName("rejeita hibrido: escada com condicoes")
        void rejeitaEscadaComCondicoes() {
            assertThatThrownBy(() -> Regra.reconstituir(ID, "chave", "PIX", "hibrida",
                    "VALOR_TRANSACAO", new BigDecimal("300"), null,
                    List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(100)))
                    .isInstanceOf(NaturezaRegraInvalidaException.class)
                    .hasMessageContaining("nao pode declarar condicoes");
        }

        @Test
        @DisplayName("rejeita escada com operador logico")
        void rejeitaEscadaComOperadorLogico() {
            assertThatThrownBy(() -> Regra.reconstituir(ID, "chave", "PIX", "hibrida",
                    "VALOR_TRANSACAO", new BigDecimal("300"), OperadorLogico.E,
                    List.of(), Acao.somar(100)))
                    .isInstanceOf(NaturezaRegraInvalidaException.class)
                    .hasMessageContaining("operador logico");
        }

        @Test
        @DisplayName("rejeita regra condicional sem nenhuma condicao")
        void rejeitaCondicionalSemCondicoes() {
            assertThatThrownBy(() -> Regra.condicional(ID, "chave", "PIX", "vazia",
                    OperadorLogico.E, List.of(), Acao.somar(100)))
                    .isInstanceOf(NaturezaRegraInvalidaException.class)
                    .hasMessageContaining("ao menos uma condicao");
        }

        @Test
        @DisplayName("rejeita limite superior em regra condicional")
        void rejeitaLimiteEmCondicional() {
            assertThatThrownBy(() -> Regra.reconstituir(ID, "chave", "PIX", "condicional",
                    null, new BigDecimal("300"), OperadorLogico.E,
                    List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(100)))
                    .isInstanceOf(NaturezaRegraInvalidaException.class)
                    .hasMessageContaining("Limite superior so tem sentido");
        }
    }

    @Nested
    @DisplayName("normalizacao e acessores")
    class Normalizacao {

        @Test
        @DisplayName("normaliza tipo de transacao para maiusculas")
        void normalizaTipo() {
            Regra regra = Regra.deEscada(ID, "chave", " pix ", "faixa", "VALOR_TRANSACAO",
                    new BigDecimal("300"), Acao.somar(200));

            assertThat(regra.tipoTransacao()).isEqualTo("PIX");
        }

        @Test
        @DisplayName("chave em branco equivale a ausencia de chave")
        void chaveEmBrancoEhNula() {
            Regra semChave = Regra.condicional(ID, "   ", "PIX", "aditiva", null,
                    List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(100));

            assertThat(semChave.temChave()).isFalse();
            assertThat(semChave.chave()).isNull();
        }

        @Test
        @DisplayName("operador logico ausente reporta E")
        void operadorAusenteReportaE() {
            Regra regra = Regra.condicional(ID, "chave", "PIX", "sem operador", null,
                    List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(100));

            assertThat(regra.operadorLogico()).isEqualTo(OperadorLogico.E);
        }

        @Test
        @DisplayName("rejeita tipo de transacao ausente")
        void rejeitaTipoAusente() {
            assertThatThrownBy(() -> Regra.deEscada(ID, "chave", "  ", "faixa", "VALOR_TRANSACAO",
                    new BigDecimal("300"), Acao.somar(200)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tipo de transacao");
        }
    }

    @Test
    @DisplayName("identidade e pelo id")
    void identidadePeloId() {
        Regra umaRegra = Regra.deEscada(ID, "a", "PIX", "d", "E", new BigDecimal("1"), Acao.somar(1));
        Regra mesmaIdentidade = Regra.deEscada(ID, "b", "TED", "outra", "E", new BigDecimal("2"), Acao.somar(2));
        Regra outraIdentidade = Regra.deEscada(UUID.randomUUID(), "a", "PIX", "d", "E",
                new BigDecimal("1"), Acao.somar(1));

        assertThat(umaRegra).isEqualTo(mesmaIdentidade).hasSameHashCodeAs(mesmaIdentidade);
        assertThat(umaRegra).isNotEqualTo(outraIdentidade).isNotEqualTo("outro tipo");
        assertThat(umaRegra.toString()).contains("a").contains("PIX");
    }

    @Nested
    @DisplayName("acao")
    class AcaoTest {

        @Test
        @DisplayName("somar contribui positivo e subtrair contribui negativo")
        void sinalDaAcao() {
            assertThat(Acao.somar(200).pontosComSinal()).isEqualTo(200);
            assertThat(Acao.subtrair(200).pontosComSinal()).isEqualTo(-200);
        }

        @Test
        @DisplayName("rejeita pontos nao positivos — o sinal vem do tipo, nao do valor")
        void rejeitaPontosNaoPositivos() {
            assertThatThrownBy(() -> Acao.somar(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positivos");
            assertThatThrownBy(() -> Acao.somar(-10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejeita tipo nulo")
        void rejeitaTipoNulo() {
            assertThatThrownBy(() -> new Acao(null, 100))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("condicao")
    class CondicaoTest {

        @Test
        @DisplayName("fabrica de conveniencia cria condicao booleana verdadeira")
        void fabricaVerdadeiro() {
            Condicao condicao = Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA);

            assertThat(condicao.campo()).isEqualTo(Campo.IP_EM_LISTA_RESTRITIVA);
            assertThat(condicao.operador()).isEqualTo(Operador.IGUAL);
            assertThat(condicao.valor()).isEqualTo("true");
        }

        @Test
        @DisplayName("rejeita campos obrigatorios ausentes")
        void rejeitaCamposAusentes() {
            assertThatThrownBy(() -> Condicao.de(null, Operador.IGUAL, "true"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Condicao.de(Campo.TIPO_TRANSACAO, null, "true"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> Condicao.de(Campo.TIPO_TRANSACAO, Operador.IGUAL, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valor da condicao");
        }
    }

    @Test
    @DisplayName("cada campo declara o tipo que aceita")
    void tipoPorCampo() {
        assertThat(Campo.VALOR_TRANSACAO.tipo()).isEqualTo(Campo.TipoCampo.DECIMAL);
        assertThat(Campo.TIPO_TRANSACAO.tipo()).isEqualTo(Campo.TipoCampo.TEXTO);
        assertThat(Campo.CPF_EM_LISTA_PERMISSIVA.tipo()).isEqualTo(Campo.TipoCampo.BOOLEANO);
    }

    @Test
    @DisplayName("operador logico omitido resolve para o padrao E")
    void operadorLogicoPadrao() {
        assertThat(OperadorLogico.ouPadrao(null)).isEqualTo(OperadorLogico.E);
        assertThat(OperadorLogico.ouPadrao(OperadorLogico.OU)).isEqualTo(OperadorLogico.OU);
        assertThat(OperadorLogico.PADRAO).isEqualTo(OperadorLogico.E);
    }
}
