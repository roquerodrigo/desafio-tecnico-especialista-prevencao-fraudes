package br.com.acme.analiserisco.aplicacao.porta;

import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.Transacao;
import java.util.List;

/**
 * Porta de calculo de score.
 *
 * <p>Diferente da consulta de listas, esta porta <b>propaga</b> a falha: sem score nao existe
 * decisao possivel, e fabricar uma seria pior que admitir a indisponibilidade.
 */
public interface CalculoScorePort {

    ResultadoCalculo calcular(Transacao transacao, ResultadoListas resultadoListas);

    record ResultadoCalculo(int score, List<RegraAcionada> regrasAcionadas) {
    }

    record RegraAcionada(String chave, String descricao, String acao, int pontos) {
    }
}
