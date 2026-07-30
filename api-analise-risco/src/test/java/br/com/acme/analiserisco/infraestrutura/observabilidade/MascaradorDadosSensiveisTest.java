package br.com.acme.analiserisco.infraestrutura.observabilidade;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MascaradorDadosSensiveisTest {

    @Test
    @DisplayName("mascara o meio do CPF, preservando prefixo e sufixo para correlacao visual")
    void mascaraCpf() {
        assertThat(MascaradorDadosSensiveis.cpf("52998224725")).isEqualTo("529****4725");
    }

    @Test
    @DisplayName("nenhum CPF completo sobrevive ao mascaramento")
    void cpfCompletoNuncaAparece() {
        String cpf = "52998224725";

        assertThat(MascaradorDadosSensiveis.cpf(cpf)).doesNotContain(cpf);
    }

    @Test
    @DisplayName("mascara o ultimo octeto do IPv4, preservando a rede de origem")
    void mascaraIpV4() {
        assertThat(MascaradorDadosSensiveis.ip("203.0.113.42")).isEqualTo("203.0.113.***");
    }

    @Test
    @DisplayName("mascara a segunda metade do IPv6")
    void mascaraIpV6() {
        String mascarado = MascaradorDadosSensiveis.ip("2001:0db8:85a3:0000:0000:8a2e:0370:7334");

        assertThat(mascarado).endsWith(":***");
        assertThat(mascarado).doesNotContain("7334");
    }

    @Test
    @DisplayName("preserva apenas os 8 primeiros caracteres do identificador de dispositivo")
    void mascaraIdDispositivo() {
        String mascarado = MascaradorDadosSensiveis.idDispositivo("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

        assertThat(mascarado).isEqualTo("3f2504e0-***");
        assertThat(mascarado).doesNotContain("0305e82c3301");
    }

    @Test
    @DisplayName("entrada nula ou malformada nao vaza nada")
    void entradaInvalidaNaoVaza() {
        assertThat(MascaradorDadosSensiveis.cpf(null)).isEqualTo("***");
        assertThat(MascaradorDadosSensiveis.cpf("123")).isEqualTo("***");
        assertThat(MascaradorDadosSensiveis.ip(null)).isEqualTo("***");
        assertThat(MascaradorDadosSensiveis.ip("")).isEqualTo("***");
        assertThat(MascaradorDadosSensiveis.ip("sem-pontos")).isEqualTo("***");
        assertThat(MascaradorDadosSensiveis.idDispositivo(null)).isEqualTo("***");
        assertThat(MascaradorDadosSensiveis.idDispositivo("curto")).isEqualTo("***");
    }
}
