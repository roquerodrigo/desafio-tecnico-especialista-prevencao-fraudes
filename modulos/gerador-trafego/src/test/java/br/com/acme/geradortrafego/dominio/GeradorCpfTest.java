package br.com.acme.geradortrafego.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeradorCpfTest {

    private final GeradorCpf gerador = new GeradorCpf(new Random(42));

    @Test
    @DisplayName("todo CPF gerado passa pelo mesmo validador que a API usa")
    void todosOsCpfsGeradosSaoValidos() {
        Set<String> invalidos = new HashSet<>();

        for (int tentativa = 0; tentativa < 1_000; tentativa++) {
            String cpf = gerador.gerar();
            if (!ValidadorCpf.valido(cpf)) {
                invalidos.add(cpf);
            }
        }

        assertThat(invalidos)
                .as("CPF invalido faria a API responder 400 e o gerador mediria latencia de validacao, "
                        + "nao de analise de risco")
                .isEmpty();
    }

    @Test
    @DisplayName("gera sempre 11 digitos numericos")
    void formatoCorreto() {
        IntStream.range(0, 100).forEach(tentativa ->
                assertThat(gerador.gerar()).hasSize(11).containsOnlyDigits());
    }

    @Test
    @DisplayName("nunca gera sequencia de digito repetido")
    void nuncaGeraSequenciaRepetida() {
        Set<String> sequenciasProibidas = new HashSet<>();
        for (int digito = 0; digito <= 9; digito++) {
            sequenciasProibidas.add(String.valueOf(digito).repeat(11));
        }

        for (int tentativa = 0; tentativa < 1_000; tentativa++) {
            assertThat(gerador.gerar()).isNotIn(sequenciasProibidas);
        }
    }

    @Test
    @DisplayName("mesma semente produz a mesma sequencia — carga reproduzivel")
    void mesmaSementeProduzMesmaSequencia() {
        GeradorCpf primeiro = new GeradorCpf(new Random(7));
        GeradorCpf segundo = new GeradorCpf(new Random(7));

        for (int tentativa = 0; tentativa < 50; tentativa++) {
            assertThat(primeiro.gerar()).isEqualTo(segundo.gerar());
        }
    }

    @Test
    @DisplayName("gera CPFs variados, nao repete o mesmo valor")
    void geraValoresVariados() {
        Set<String> gerados = new HashSet<>();
        for (int tentativa = 0; tentativa < 500; tentativa++) {
            gerados.add(gerador.gerar());
        }

        assertThat(gerados).hasSizeGreaterThan(490);
    }
}
