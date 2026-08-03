package br.com.acme.analiserisco.dominio;

import java.util.List;

/**
 * Reune todos os problemas de validacao de uma vez. O cliente corrige tudo numa tentativa, em vez
 * de descobrir um erro por requisicao.
 */
public class TransacaoInvalidaException extends RuntimeException {

    private final List<String> problemas;

    public TransacaoInvalidaException(List<String> problemas) {
        super("Transacao invalida: " + String.join("; ", problemas));
        this.problemas = List.copyOf(problemas);
    }

    public List<String> problemas() {
        return problemas;
    }
}
