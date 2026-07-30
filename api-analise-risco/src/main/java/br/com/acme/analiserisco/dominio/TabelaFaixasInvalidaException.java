package br.com.acme.analiserisco.dominio;

public class TabelaFaixasInvalidaException extends RuntimeException {

    public TabelaFaixasInvalidaException(String mensagem) {
        super(mensagem);
    }
}
