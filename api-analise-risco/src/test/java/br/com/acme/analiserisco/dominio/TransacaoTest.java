package br.com.acme.analiserisco.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransacaoTest {

    private static final String CPF_VALIDO = "52998224725";
    private static final UUID DISPOSITIVO = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static Transacao transacao(String cpf, String ip, UUID dispositivo, String tipo, String valor) {
        return new Transacao(cpf, ip, dispositivo, tipo,
                valor == null ? null : new BigDecimal(valor));
    }

    @Test
    @DisplayName("constroi transacao valida")
    void constroiTransacaoValida() {
        Transacao transacao = transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "PIX", "1500.00");

        assertThat(transacao.cpf()).isEqualTo(CPF_VALIDO);
        assertThat(transacao.tipoTransacao()).isEqualTo("PIX");
        assertThat(transacao.valorTransacao()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("normaliza o tipo de transacao para maiusculas, evitando conjuntos duplicados por digitacao")
    void normalizaTipoParaMaiusculas() {
        assertThat(transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "pix", "100.00").tipoTransacao())
                .isEqualTo("PIX");
        assertThat(transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "  Cartao  ", "100.00").tipoTransacao())
                .isEqualTo("CARTAO");
    }

    @Test
    @DisplayName("rejeita CPF invalido")
    void rejeitaCpfInvalido() {
        assertThatThrownBy(() -> transacao("11111111111", "203.0.113.42", DISPOSITIVO, "PIX", "100.00"))
                .isInstanceOf(TransacaoInvalidaException.class)
                .hasMessageContaining("cpf");
    }

    @Test
    @DisplayName("rejeita valor zero — nenhuma faixa cobre zero")
    void rejeitaValorZero() {
        assertThatThrownBy(() -> transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "PIX", "0.00"))
                .isInstanceOf(TransacaoInvalidaException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    @DisplayName("rejeita valor negativo")
    void rejeitaValorNegativo() {
        assertThatThrownBy(() -> transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "PIX", "-10.00"))
                .isInstanceOf(TransacaoInvalidaException.class)
                .hasMessageContaining("maior que zero");
    }

    @Test
    @DisplayName("rejeita valor com tres casas decimais — centavo e a menor unidade")
    void rejeitaTresCasasDecimais() {
        assertThatThrownBy(() -> transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "PIX", "300.005"))
                .isInstanceOf(TransacaoInvalidaException.class)
                .hasMessageContaining("2 casas decimais");
    }

    @Test
    @DisplayName("aceita valor com uma ou duas casas decimais")
    void aceitaAteDuasCasasDecimais() {
        assertThat(transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "PIX", "300.5").valorTransacao())
                .isEqualByComparingTo("300.50");
        assertThat(transacao(CPF_VALIDO, "203.0.113.42", DISPOSITIVO, "PIX", "300").valorTransacao())
                .isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("reune todos os problemas de uma vez, para o cliente corrigir tudo numa tentativa")
    void reuneTodosOsProblemas() {
        TransacaoInvalidaException excecao = catchThrowableOfType(TransacaoInvalidaException.class,
                () -> transacao("11111111111", "", null, "", "0"));

        assertThat(excecao.problemas())
                .hasSizeGreaterThanOrEqualTo(4)
                .anySatisfy(problema -> assertThat(problema).contains("cpf"))
                .anySatisfy(problema -> assertThat(problema).contains("ip"))
                .anySatisfy(problema -> assertThat(problema).contains("idDispositivo"))
                .anySatisfy(problema -> assertThat(problema).contains("tipoTransacao"));
    }
}
