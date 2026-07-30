package br.com.acme.motordecisao.aplicacao;

/**
 * Lancada quando nao existe conjunto de regras confiavel para avaliar a transacao.
 *
 * <p>Sem regras confiaveis o motor prefere nao pontuar a pontuar errado: um score fabricado
 * seria interpretado como decisao de risco legitima pela orquestracao.
 */
public class ConjuntoRegrasIndisponivelException extends RuntimeException {

    public ConjuntoRegrasIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
