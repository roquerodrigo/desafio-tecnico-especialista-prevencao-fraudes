package br.com.acme.listas.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorCpfTest {

    @ParameterizedTest(name = "aceita o CPF valido {0}")
    @ValueSource(strings = {"52998224725", "11144477735", "12345678909", "00000000191"})
    void aceitaCpfValido(String cpf) {
        assertThat(ValidadorCpf.valido(cpf)).isTrue();
    }

    @ParameterizedTest(name = "rejeita a sequencia repetida {0}")
    @ValueSource(strings = {
            "00000000000", "11111111111", "22222222222", "33333333333", "44444444444",
            "55555555555", "66666666666", "77777777777", "88888888888", "99999999999"
    })
    @DisplayName("rejeita sequencias de digito repetido, que satisfazem o modulo 11 mas nao sao CPFs validos")
    void rejeitaSequenciasRepetidas(String cpf) {
        assertThat(ValidadorCpf.valido(cpf))
                .as("%s passa no calculo do digito verificador; sem rejeicao explicita seria aceito", cpf)
                .isFalse();
    }

    @ParameterizedTest(name = "rejeita {0} por digito verificador incorreto")
    @ValueSource(strings = {"52998224724", "12345678900", "11144477730"})
    void rejeitaDigitoVerificadorIncorreto(String cpf) {
        assertThat(ValidadorCpf.valido(cpf)).isFalse();
    }

    @ParameterizedTest(name = "rejeita entrada malformada: '{0}'")
    @ValueSource(strings = {
            "5299822472",      // 10 digitos
            "529982247250",    // 12 digitos
            "529.982.247-25",  // com mascara
            "5299822472a",     // com letra
            "           ",     // apenas espacos
            ""
    })
    void rejeitaEntradaMalformada(String cpf) {
        assertThat(ValidadorCpf.valido(cpf)).isFalse();
    }

    @Test
    @DisplayName("rejeita nulo")
    void rejeitaNulo() {
        assertThat(ValidadorCpf.valido(null)).isFalse();
    }
}
