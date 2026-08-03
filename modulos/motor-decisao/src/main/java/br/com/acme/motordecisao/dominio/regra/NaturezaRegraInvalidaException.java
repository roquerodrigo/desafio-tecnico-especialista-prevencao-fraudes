package br.com.acme.motordecisao.dominio.regra;

/**
 * Lancada quando uma regra tentaria existir como hibrido de escada e condicional, ou sem
 * nenhuma das duas naturezas.
 */
public class NaturezaRegraInvalidaException extends RuntimeException {

    public NaturezaRegraInvalidaException(String mensagem) {
        super(mensagem);
    }
}
