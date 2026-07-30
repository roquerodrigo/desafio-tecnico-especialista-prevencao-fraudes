package br.com.acme.geradortrafego.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica que o trafego gerado exercita o dominio de verdade.
 *
 * <p>Um gerador uniformemente aleatorio produziria carga que cai quase sempre na mesma faixa e
 * quase nunca em lista restritiva — mediria latencia de um unico caminho de codigo e mostraria
 * 100% de aprovacao. A semente e fixa para que as proporcoes sejam deterministicas.
 */
class PerfilTrafegoTest {

    private static final int AMOSTRAS = 10_000;

    private List<PerfilTrafego.TransacaoSintetica> transacoes;

    @BeforeEach
    void sortearAmostras() {
        PerfilTrafego perfil = new PerfilTrafego(new Random(2026));
        transacoes = new ArrayList<>(AMOSTRAS);
        for (int contador = 0; contador < AMOSTRAS; contador++) {
            transacoes.add(perfil.sortear());
        }
    }

    @Test
    @DisplayName("todo CPF sorteado e valido, inclusive os da massa semeada")
    void todosOsCpfsSaoValidos() {
        assertThat(transacoes)
                .allSatisfy(transacao -> assertThat(ValidadorCpf.valido(transacao.cpf()))
                        .as("CPF %s deveria ser valido", transacao.cpf())
                        .isTrue());
    }

    @Test
    @DisplayName("todo valor tem no maximo duas casas decimais, como a API exige")
    void valoresComDuasCasasDecimais() {
        assertThat(transacoes)
                .allSatisfy(transacao -> assertThat(transacao.valorTransacao().scale()).isLessThanOrEqualTo(2));
    }

    @Test
    @DisplayName("todo valor e positivo — valor zero seria rejeitado com 400")
    void valoresPositivos() {
        assertThat(transacoes)
                .allSatisfy(transacao -> assertThat(transacao.valorTransacao().signum()).isPositive());
    }

    @Test
    @DisplayName("exercita os tres tipos de transacao com conjunto proprio de regras")
    void distribuiEntreOsTresTipos() {
        Map<String, Integer> contagem = contarPor(PerfilTrafego.TransacaoSintetica::tipoTransacao);

        assertThat(contagem).containsOnlyKeys("PIX", "CARTAO", "TED");
        assertThat(contagem.get("PIX")).isBetween(5_500, 6_500);
        assertThat(contagem.get("CARTAO")).isBetween(2_000, 3_000);
        assertThat(contagem.get("TED")).isBetween(1_000, 2_000);
    }

    @Test
    @DisplayName("exercita as quatro faixas de valor do enunciado")
    void distribuiEntreAsQuatroFaixas() {
        Map<String, Integer> porFaixa = new HashMap<>();
        for (PerfilTrafego.TransacaoSintetica transacao : transacoes) {
            porFaixa.merge(faixaDe(transacao.valorTransacao()), 1, Integer::sum);
        }

        assertThat(porFaixa)
                .as("as quatro faixas precisam ser atingidas, senao a escada nunca e exercitada")
                .containsOnlyKeys("faixa_1", "faixa_2", "faixa_3", "faixa_4");
        assertThat(porFaixa.values()).allSatisfy(quantidade ->
                assertThat(quantidade).isGreaterThan(500));
    }

    @Test
    @DisplayName("uma fracao relevante dos CPFs consta em listas — sem isso, tudo seria aprovado")
    void fracaoDeCpfsEmListas() {
        long emListas = transacoes.stream()
                .filter(transacao -> List.of(
                                PerfilTrafego.CPF_EM_LISTA_RESTRITIVA,
                                PerfilTrafego.CPF_EM_LISTA_PERMISSIVA,
                                PerfilTrafego.CPF_EM_AMBAS_AS_LISTAS)
                        .contains(transacao.cpf()))
                .count();

        assertThat(emListas).isBetween(1_700L, 2_300L);
    }

    @Test
    @DisplayName("uma fracao dos IPs e dispositivos consta em lista restritiva")
    void fracaoDeIpEDispositivoEmListas() {
        long ipsRestritivos = transacoes.stream()
                .filter(transacao -> PerfilTrafego.IP_EM_LISTA_RESTRITIVA.equals(transacao.ip()))
                .count();
        long dispositivosRestritivos = transacoes.stream()
                .filter(transacao -> PerfilTrafego.DISPOSITIVO_EM_LISTA_RESTRITIVA.equals(transacao.idDispositivo()))
                .count();

        assertThat(ipsRestritivos).isBetween(700L, 1_300L);
        assertThat(dispositivosRestritivos).isBetween(700L, 1_300L);
    }

    @Test
    @DisplayName("mesma semente produz a mesma carga — medicoes comparaveis entre execucoes")
    void mesmaSementeProduzMesmaCarga() {
        PerfilTrafego primeiro = new PerfilTrafego(new Random(99));
        PerfilTrafego segundo = new PerfilTrafego(new Random(99));

        for (int contador = 0; contador < 100; contador++) {
            assertThat(primeiro.sortear()).isEqualTo(segundo.sortear());
        }
    }

    private Map<String, Integer> contarPor(
            java.util.function.Function<PerfilTrafego.TransacaoSintetica, String> extrator) {
        Map<String, Integer> contagem = new HashMap<>();
        transacoes.forEach(transacao -> contagem.merge(extrator.apply(transacao), 1, Integer::sum));
        return contagem;
    }

    private String faixaDe(BigDecimal valor) {
        if (valor.compareTo(new BigDecimal("300.00")) <= 0) {
            return "faixa_1";
        }
        if (valor.compareTo(new BigDecimal("5000.00")) <= 0) {
            return "faixa_2";
        }
        if (valor.compareTo(new BigDecimal("20000.00")) <= 0) {
            return "faixa_3";
        }
        return "faixa_4";
    }
}
