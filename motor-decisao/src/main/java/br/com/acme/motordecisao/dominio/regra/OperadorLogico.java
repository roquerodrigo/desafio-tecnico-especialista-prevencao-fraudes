package br.com.acme.motordecisao.dominio.regra;

/**
 * Combina as condicoes de uma regra. Ausente, assume-se {@link #E}, conforme o enunciado.
 */
public enum OperadorLogico {
    E,
    OU;

    public static final OperadorLogico PADRAO = E;

    public static OperadorLogico ouPadrao(OperadorLogico operador) {
        return operador == null ? PADRAO : operador;
    }
}
