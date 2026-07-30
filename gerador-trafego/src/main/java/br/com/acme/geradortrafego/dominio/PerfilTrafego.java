package br.com.acme.geradortrafego.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Sorteia transacoes com distribuicao calibrada para exercitar o dominio.
 *
 * <p>Trafego uniformemente aleatorio cairia quase sempre na mesma faixa de valor e quase nunca em
 * lista restritiva — mediria latencia de um unico caminho de codigo. As proporcoes abaixo garantem
 * que as quatro faixas, os tres tipos de transacao e os sinais de lista sejam efetivamente
 * percorridos.
 *
 * <p>Os CPFs conhecidos vem da massa semeada por {@code infra/seed-listas.sh}. Sao eles que
 * produzem as negativas por lista restritiva; sem essa fracao, o relatorio mostraria 100% de
 * aprovacao e nao provaria nada sobre a composicao de regras.
 */
public final class PerfilTrafego {

    /** CPFs da massa semeada, que constam em listas. */
    public static final String CPF_EM_LISTA_RESTRITIVA = "11144477735";
    public static final String CPF_EM_LISTA_PERMISSIVA = "12345678909";
    public static final String CPF_EM_AMBAS_AS_LISTAS = "00000000191";

    /** IP e dispositivo em lista restritiva, tambem da massa semeada. */
    public static final String IP_EM_LISTA_RESTRITIVA = "198.51.100.66";
    public static final String DISPOSITIVO_EM_LISTA_RESTRITIVA = "99999999-9999-4999-8999-999999999999";

    private static final List<String> CPFS_CONHECIDOS = List.of(
            CPF_EM_LISTA_RESTRITIVA, CPF_EM_LISTA_PERMISSIVA, CPF_EM_AMBAS_AS_LISTAS);

    private static final int PERCENTUAL_CPF_EM_LISTA = 20;
    private static final int PERCENTUAL_IP_OU_DISPOSITIVO_EM_LISTA = 10;

    private final RandomGenerator sorteio;
    private final GeradorCpf geradorCpf;

    public PerfilTrafego(RandomGenerator sorteio) {
        this.sorteio = sorteio;
        this.geradorCpf = new GeradorCpf(sorteio);
    }

    public TransacaoSintetica sortear() {
        return new TransacaoSintetica(
                sortearCpf(),
                sortearIp(),
                sortearDispositivo(),
                sortearTipoTransacao(),
                sortearValor());
    }

    /** 20% da massa semeada (consta em lista), 80% gerado (nao consta). */
    private String sortearCpf() {
        if (sorteio.nextInt(100) < PERCENTUAL_CPF_EM_LISTA) {
            return CPFS_CONHECIDOS.get(sorteio.nextInt(CPFS_CONHECIDOS.size()));
        }
        return geradorCpf.gerar();
    }

    private String sortearIp() {
        if (sorteio.nextInt(100) < PERCENTUAL_IP_OU_DISPOSITIVO_EM_LISTA) {
            return IP_EM_LISTA_RESTRITIVA;
        }
        return "198.51.100." + (sorteio.nextInt(200) + 10);
    }

    private String sortearDispositivo() {
        if (sorteio.nextInt(100) < PERCENTUAL_IP_OU_DISPOSITIVO_EM_LISTA) {
            return DISPOSITIVO_EM_LISTA_RESTRITIVA;
        }
        return new java.util.UUID(sorteio.nextLong(), sorteio.nextLong()).toString();
    }

    /** 60% PIX, 25% CARTAO, 15% TED — os tres tipos com conjunto proprio de regras. */
    private String sortearTipoTransacao() {
        int sorteado = sorteio.nextInt(100);
        if (sorteado < 60) {
            return "PIX";
        }
        if (sorteado < 85) {
            return "CARTAO";
        }
        return "TED";
    }

    /**
     * Distribuicao entre as quatro faixas: 40% ate R$300, 35% ate R$5.000, 15% ate R$20.000,
     * 10% acima. Sempre com no maximo duas casas decimais, como a API exige.
     */
    private BigDecimal sortearValor() {
        int sorteado = sorteio.nextInt(100);
        if (sorteado < 40) {
            return valorEntre(1, 30_000);
        }
        if (sorteado < 75) {
            return valorEntre(30_001, 500_000);
        }
        if (sorteado < 90) {
            return valorEntre(500_001, 2_000_000);
        }
        return valorEntre(2_000_001, 10_000_000);
    }

    /** Sorteia em centavos e converte, o que garante duas casas decimais exatas. */
    private BigDecimal valorEntre(int centavosMinimo, int centavosMaximo) {
        int centavos = centavosMinimo + sorteio.nextInt(centavosMaximo - centavosMinimo + 1);
        return BigDecimal.valueOf(centavos, 2).setScale(2, RoundingMode.UNNECESSARY);
    }

    public record TransacaoSintetica(
            String cpf,
            String ip,
            String idDispositivo,
            String tipoTransacao,
            BigDecimal valorTransacao) {
    }
}
