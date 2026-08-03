package br.com.acme.geradortrafego.dominio;

import java.util.random.RandomGenerator;

/**
 * Gera CPFs com digito verificador valido.
 *
 * <p>Nao e detalhe de conveniencia: a API de analise valida o digito verificador, entao um gerador
 * ingenuo receberia {@code 400} em 100% das requisicoes e mediria a latencia da validacao de
 * entrada, nao a da analise de risco. O tempo medido seria real, mas responderia a pergunta errada.
 *
 * <p>Tambem evita sequencias de digito repetido, que satisfazem o calculo do modulo 11 mas sao
 * rejeitadas pela API — os mesmos onze numeros que o {@code ValidadorCpf} barra explicitamente.
 */
public final class GeradorCpf {

    private static final int DIGITOS_BASE = 9;

    private final RandomGenerator sorteio;

    public GeradorCpf(RandomGenerator sorteio) {
        this.sorteio = sorteio;
    }

    public String gerar() {
        int[] digitos = new int[11];
        do {
            for (int posicao = 0; posicao < DIGITOS_BASE; posicao++) {
                digitos[posicao] = sorteio.nextInt(10);
            }
        } while (todosIguais(digitos));

        digitos[9] = calcularDigitoVerificador(digitos, DIGITOS_BASE);
        digitos[10] = calcularDigitoVerificador(digitos, DIGITOS_BASE + 1);

        StringBuilder cpf = new StringBuilder(11);
        for (int digito : digitos) {
            cpf.append(digito);
        }
        return cpf.toString();
    }

    private boolean todosIguais(int[] digitos) {
        for (int posicao = 1; posicao < DIGITOS_BASE; posicao++) {
            if (digitos[posicao] != digitos[0]) {
                return false;
            }
        }
        return true;
    }

    private int calcularDigitoVerificador(int[] digitos, int quantidade) {
        int peso = quantidade + 1;
        int soma = 0;
        for (int posicao = 0; posicao < quantidade; posicao++) {
            soma += digitos[posicao] * (peso - posicao);
        }
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }
}
