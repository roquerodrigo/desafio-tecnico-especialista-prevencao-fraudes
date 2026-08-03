package br.com.acme.motordecisao.aplicacao;

import java.util.UUID;

public class RegraNaoEncontradaException extends RuntimeException {

    public RegraNaoEncontradaException(UUID id) {
        super("Regra nao encontrada: " + id);
    }
}
