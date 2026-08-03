package br.com.acme.motordecisao.dominio.regra;

/**
 * Lancada quando uma escada viola suas invariantes estruturais. Sempre na carga do cache, nunca
 * durante a avaliacao de uma transacao.
 */
public class EscadaInvalidaException extends RuntimeException {

    public EscadaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
