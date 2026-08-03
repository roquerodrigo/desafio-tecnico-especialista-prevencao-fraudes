package br.com.acme.listas.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DominioListasTest {

    private static final Instant AGORA = Instant.parse("2026-07-29T12:00:00Z");
    private static final String CPF_VALIDO = "52998224725";
    private static final String ID_LISTA = "4a7e0000-0000-4000-8000-000000000001";

    @Nested
    @DisplayName("tipo de variavel")
    class Tipos {

        @Test
        @DisplayName("somente CPF admite lista permissiva")
        void permissivaSomenteParaCpf() {
            assertThat(TipoVariavel.CPF.admitePermissiva()).isTrue();
            assertThat(TipoVariavel.IP.admitePermissiva()).isFalse();
            assertThat(TipoVariavel.DISPOSITIVO.admitePermissiva()).isFalse();
        }

        @Test
        @DisplayName("somente IP admite expiracao — enderecos sao reatribuidos")
        void expiracaoSomenteParaIp() {
            assertThat(TipoVariavel.IP.admiteExpiracao()).isTrue();
            assertThat(TipoVariavel.CPF.admiteExpiracao()).isFalse();
            assertThat(TipoVariavel.DISPOSITIVO.admiteExpiracao()).isFalse();
        }
    }

    @Nested
    @DisplayName("efetividade da pertinencia")
    class Efetividade {

        @Test
        @DisplayName("pertinencia ativa sem expiracao e sempre efetiva")
        void ativaSemExpiracao() {
            Pertinencia pertinencia = new Pertinencia(ID_LISTA, Pertinencia.Situacao.ATIVA, AGORA, null);

            assertThat(pertinencia.efetivaEm(AGORA)).isTrue();
        }

        @Test
        @DisplayName("pertinencia inativa nunca e efetiva")
        void inativaNaoEhEfetiva() {
            Pertinencia pertinencia = new Pertinencia(ID_LISTA, Pertinencia.Situacao.INATIVA, AGORA, null);

            assertThat(pertinencia.efetivaEm(AGORA)).isFalse();
        }

        @Test
        @DisplayName("pertinencia expirada nao e efetiva, mesmo ativa")
        void expiradaNaoEhEfetiva() {
            Pertinencia expirada = new Pertinencia(ID_LISTA, Pertinencia.Situacao.ATIVA, AGORA,
                    AGORA.minus(1, ChronoUnit.DAYS));

            assertThat(expirada.efetivaEm(AGORA))
                    .as("o expurgo por TTL pode levar dias; a expiracao precisa valer na leitura")
                    .isFalse();
        }

        @Test
        @DisplayName("pertinencia com expiracao futura e efetiva")
        void expiracaoFuturaEhEfetiva() {
            Pertinencia valida = new Pertinencia(ID_LISTA, Pertinencia.Situacao.ATIVA, AGORA,
                    AGORA.plus(30, ChronoUnit.DAYS));

            assertThat(valida.efetivaEm(AGORA)).isTrue();
        }

        @Test
        @DisplayName("fabrica de conveniencia cria pertinencia ativa sem prazo")
        void fabricaAtiva() {
            Pertinencia pertinencia = Pertinencia.ativa(ID_LISTA);

            assertThat(pertinencia.situacao()).isEqualTo(Pertinencia.Situacao.ATIVA);
            assertThat(pertinencia.expiraEm()).isNull();
        }

        @Test
        @DisplayName("rejeita campos obrigatorios ausentes")
        void rejeitaCamposAusentes() {
            assertThatThrownBy(() -> new Pertinencia(null, Pertinencia.Situacao.ATIVA, AGORA, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Pertinencia(ID_LISTA, null, AGORA, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("resultado da consulta de variavel")
    class Resultado {

        @Test
        @DisplayName("ausente nao consta em nenhuma lista")
        void ausente() {
            ResultadoVariavel resultado = ResultadoVariavel.ausente(CPF_VALIDO);

            assertThat(resultado.constaEmPermissiva()).isFalse();
            assertThat(resultado.constaEmRestritiva()).isFalse();
            assertThat(resultado.valor()).isEqualTo(CPF_VALIDO);
        }

        @Test
        @DisplayName("CPF pode constar em ambas as listas simultaneamente")
        void constaEmAmbas() {
            ResultadoVariavel resultado = ResultadoVariavel.de(CPF_VALIDO,
                    Pertinencia.ativa(ID_LISTA), Pertinencia.ativa(ID_LISTA), AGORA);

            assertThat(resultado.constaEmPermissiva()).isTrue();
            assertThat(resultado.constaEmRestritiva()).isTrue();
        }

        @Test
        @DisplayName("pertinencia inefetiva vira ausencia para o consumidor")
        void inefetivaViraAusencia() {
            Pertinencia expirada = new Pertinencia(ID_LISTA, Pertinencia.Situacao.ATIVA, AGORA,
                    AGORA.minus(1, ChronoUnit.DAYS));
            Pertinencia inativa = new Pertinencia(ID_LISTA, Pertinencia.Situacao.INATIVA, AGORA, null);

            ResultadoVariavel resultado = ResultadoVariavel.de(CPF_VALIDO, inativa, expirada, AGORA);

            assertThat(resultado.constaEmPermissiva()).isFalse();
            assertThat(resultado.constaEmRestritiva()).isFalse();
            assertThat(resultado.permissiva()).isNull();
            assertThat(resultado.restritiva()).isNull();
        }
    }

    @Nested
    @DisplayName("validacao da entrada de carga")
    class Entrada {

        @Test
        @DisplayName("aceita CPF com as duas pertinencias")
        void cpfComAmbas() {
            EntradaLista entrada = new EntradaLista(TipoVariavel.CPF, CPF_VALIDO,
                    Pertinencia.ativa(ID_LISTA), Pertinencia.ativa(ID_LISTA), null);

            assertThat(entrada.tipo()).isEqualTo(TipoVariavel.CPF);
        }

        @Test
        @DisplayName("rejeita lista permissiva em IP")
        void rejeitaPermissivaEmIp() {
            EntradaListaInvalidaException excecao = catchThrowableOfType(EntradaListaInvalidaException.class,
                    () -> new EntradaLista(TipoVariavel.IP, "203.0.113.42",
                            Pertinencia.ativa(ID_LISTA), null, null));

            assertThat(excecao.problemas()).anySatisfy(problema ->
                    assertThat(problema).contains("apenas CPF admite lista permissiva"));
        }

        @Test
        @DisplayName("rejeita expiracao em CPF")
        void rejeitaExpiracaoEmCpf() {
            EntradaListaInvalidaException excecao = catchThrowableOfType(EntradaListaInvalidaException.class,
                    () -> new EntradaLista(TipoVariavel.CPF, CPF_VALIDO, null,
                            Pertinencia.ativa(ID_LISTA), AGORA.plus(1, ChronoUnit.DAYS)));

            assertThat(excecao.problemas()).anySatisfy(problema ->
                    assertThat(problema).contains("apenas IP admite prazo"));
        }

        @Test
        @DisplayName("rejeita CPF invalido na carga")
        void rejeitaCpfInvalido() {
            assertThatThrownBy(() -> new EntradaLista(TipoVariavel.CPF, "11111111111", null,
                    Pertinencia.ativa(ID_LISTA), null))
                    .isInstanceOf(EntradaListaInvalidaException.class)
                    .hasMessageContaining("CPF invalido");
        }

        @Test
        @DisplayName("rejeita entrada sem nenhuma pertinencia")
        void rejeitaSemPertinencia() {
            assertThatThrownBy(() -> new EntradaLista(TipoVariavel.CPF, CPF_VALIDO, null, null, null))
                    .isInstanceOf(EntradaListaInvalidaException.class)
                    .hasMessageContaining("ao menos uma lista");
        }

        @Test
        @DisplayName("rejeita valor em branco")
        void rejeitaValorEmBranco() {
            assertThatThrownBy(() -> new EntradaLista(TipoVariavel.IP, "  ", null,
                    Pertinencia.ativa(ID_LISTA), null))
                    .isInstanceOf(EntradaListaInvalidaException.class)
                    .hasMessageContaining("valor da variavel");
        }

        @Test
        @DisplayName("aceita IP com prazo de validade")
        void aceitaIpComPrazo() {
            EntradaLista entrada = new EntradaLista(TipoVariavel.IP, "203.0.113.42", null,
                    Pertinencia.ativa(ID_LISTA), AGORA.plus(30, ChronoUnit.DAYS));

            assertThat(entrada.expiraEm()).isNotNull();
        }
    }
}
