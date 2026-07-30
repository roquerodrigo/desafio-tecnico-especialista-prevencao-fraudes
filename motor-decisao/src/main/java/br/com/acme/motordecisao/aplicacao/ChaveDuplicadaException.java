package br.com.acme.motordecisao.aplicacao;

/**
 * Lancada quando a chave de sobreposicao ja existe para o tipo de transacao.
 *
 * <p>Duas regras de mesma chave no mesmo conjunto tornariam a composicao indeterminada: nao
 * haveria como saber qual delas substitui a regra padrao.
 */
public class ChaveDuplicadaException extends RuntimeException {

    public ChaveDuplicadaException(String chave, String tipoTransacao) {
        super("A chave '" + chave + "' ja existe para o tipo de transacao '" + tipoTransacao + "'");
    }
}
