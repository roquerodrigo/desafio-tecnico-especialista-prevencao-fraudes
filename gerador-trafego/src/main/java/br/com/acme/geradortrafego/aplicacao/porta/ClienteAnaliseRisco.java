package br.com.acme.geradortrafego.aplicacao.porta;

import br.com.acme.geradortrafego.dominio.PerfilTrafego;

/**
 * Porta de saida para a API de analise de risco.
 *
 * <p>Devolve um resultado em vez de lancar excecao: no gerador, uma requisicao com erro e um
 * <b>dado da medicao</b>, nao um acidente. Interromper a carga na primeira falha esconderia
 * justamente o comportamento que interessa observar sob volume.
 */
public interface ClienteAnaliseRisco {

    RespostaAnalise analisar(PerfilTrafego.TransacaoSintetica transacao);

    /**
     * @param decisao devolvida pela API, ou {@code null} quando houve erro
     * @param motivoErro descricao curta e agrupavel, ou {@code null} em caso de sucesso
     */
    record RespostaAnalise(boolean sucesso, String decisao, String motivoErro) {

        public static RespostaAnalise sucesso(String decisao) {
            return new RespostaAnalise(true, decisao, null);
        }

        public static RespostaAnalise erro(String motivoErro) {
            return new RespostaAnalise(false, null, motivoErro);
        }
    }
}
