package br.com.acme.geradortrafego.dominio;

import java.util.List;

/**
 * Percentil pelo metodo <b>nearest-rank</b>.
 *
 * <p>Para uma amostra ordenada de tamanho {@code n}, o percentil {@code p} e o elemento na posicao
 * {@code ceil(p/100 × n)}, contada a partir de 1. Sem interpolacao: o valor devolvido e sempre uma
 * medicao real que aconteceu, nunca uma media entre duas.
 *
 * <p>O metodo esta declarado porque percentil e ambiguo — existem pelo menos nove definicoes, e
 * bibliotecas diferentes devolvem valores diferentes para a mesma amostra. Um p95 sem o metodo
 * declarado nao e verificavel.
 *
 * <p>Complexidade: <b>O(n log n)</b> pela ordenacao, feita uma unica vez ao final da carga.
 * Memoria O(n) — aceitavel para cargas de demonstracao; volumes maiores pediriam um histograma de
 * memoria constante.
 */
public final class CalculadoraPercentil {

    private CalculadoraPercentil() {
    }

    /**
     * @param amostrasOrdenadas amostras em ordem crescente
     * @param percentil valor entre 0 e 100
     * @return a medicao correspondente, ou 0 se nao houver amostras
     */
    public static long percentil(List<Long> amostrasOrdenadas, int percentil) {
        if (percentil < 0 || percentil > 100) {
            throw new IllegalArgumentException("percentil deve estar entre 0 e 100; recebido: " + percentil);
        }
        if (amostrasOrdenadas.isEmpty()) {
            return 0L;
        }

        int posicao = (int) Math.ceil(percentil / 100.0 * amostrasOrdenadas.size());
        int indice = Math.max(0, Math.min(posicao - 1, amostrasOrdenadas.size() - 1));
        return amostrasOrdenadas.get(indice);
    }

    public static double media(List<Long> amostras) {
        if (amostras.isEmpty()) {
            return 0.0;
        }
        long soma = 0;
        for (long amostra : amostras) {
            soma += amostra;
        }
        return (double) soma / amostras.size();
    }
}
