package br.com.acme.listas.dominio;

import java.util.List;

public class EntradaListaInvalidaException extends RuntimeException {

    private final List<String> problemas;

    public EntradaListaInvalidaException(List<String> problemas) {
        super("Entrada de lista invalida: " + String.join("; ", problemas));
        this.problemas = List.copyOf(problemas);
    }

    public List<String> problemas() {
        return problemas;
    }
}
