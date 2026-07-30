package br.com.acme.geradortrafego.dominio;

/**
 * Valida CPF por digito verificador (modulo 11).
 *
 * <p>Rejeita explicitamente sequencias de digito repetido. Isso importa: {@code 11111111111} e as
 * outras dez sequencias <b>satisfazem</b> o calculo do digito verificador, mas nao sao CPFs
 * validos. Validar apenas o algoritmo deixaria passar onze numeros invalidos — exatamente os
 * mais provaveis de aparecer em teste malicioso ou em preenchimento automatico.
 */
public final class ValidadorCpf {

    private static final int TAMANHO = 11;

    private ValidadorCpf() {
    }

    public static boolean valido(String cpf) {
        if (cpf == null || cpf.length() != TAMANHO || !cpf.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (todosOsDigitosIguais(cpf)) {
            return false;
        }
        return digitoVerificador(cpf, 9) == numeroNaPosicao(cpf, 9)
                && digitoVerificador(cpf, 10) == numeroNaPosicao(cpf, 10);
    }

    private static boolean todosOsDigitosIguais(String cpf) {
        char primeiro = cpf.charAt(0);
        return cpf.chars().allMatch(digito -> digito == primeiro);
    }

    private static int digitoVerificador(String cpf, int posicao) {
        int peso = posicao + 1;
        int soma = 0;
        for (int indice = 0; indice < posicao; indice++) {
            soma += numeroNaPosicao(cpf, indice) * (peso - indice);
        }
        int resto = soma * 10 % 11;
        return resto == 10 ? 0 : resto;
    }

    private static int numeroNaPosicao(String cpf, int indice) {
        return Character.getNumericValue(cpf.charAt(indice));
    }
}
