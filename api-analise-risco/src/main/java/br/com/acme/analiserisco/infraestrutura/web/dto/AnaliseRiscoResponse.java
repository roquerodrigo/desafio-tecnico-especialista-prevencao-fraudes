package br.com.acme.analiserisco.infraestrutura.web.dto;

import br.com.acme.analiserisco.dominio.Decisao;

/**
 * Resposta ao cliente. <b>Contem exclusivamente a decisao.</b>
 *
 * <p>Score, classificacao e regras acionadas MUST NOT aparecer aqui, nem em cabecalho, nem em
 * mensagem de erro (FR-008). Este record ter um unico componente e a garantia estrutural disso:
 * nao existe campo onde vazar.
 */
public record AnaliseRiscoResponse(Decisao decisao) {
}
