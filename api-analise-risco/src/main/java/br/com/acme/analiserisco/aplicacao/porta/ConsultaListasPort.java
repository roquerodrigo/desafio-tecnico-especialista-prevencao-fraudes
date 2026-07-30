package br.com.acme.analiserisco.aplicacao.porta;

import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.Transacao;

/**
 * Porta de consulta de listas.
 *
 * <p>O contrato e deliberadamente livre de excecao: a implementacao MUST tratar falha
 * internamente e devolver {@link ResultadoListas#semConsulta()}. Isso codifica a decisao de que
 * lista e sinal desejavel, nao essencial — quem orquestra nao precisa decidir o que fazer com o
 * erro, porque a politica de degradacao pertence a fronteira.
 */
public interface ConsultaListasPort {

    ResultadoListas consultar(Transacao transacao);
}
