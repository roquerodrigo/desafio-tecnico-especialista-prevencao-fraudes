package br.com.acme.motordecisao.dominio.avaliacao;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Insumo unico da avaliacao: dados da transacao mais o resultado ja apurado das listas.
 *
 * <p>O motor nao consulta listas — ele recebe os sinais. Isso o mantem puro (mesma entrada,
 * mesmo score) e testavel sem rede.
 */
public record ContextoAvaliacao(
        String tipoTransacao,
        BigDecimal valorTransacao,
        boolean cpfEmListaPermissiva,
        boolean cpfEmListaRestritiva,
        boolean ipEmListaRestritiva,
        boolean dispositivoEmListaRestritiva) {

    public ContextoAvaliacao {
        if (tipoTransacao == null || tipoTransacao.isBlank()) {
            throw new IllegalArgumentException("tipo de transacao e obrigatorio");
        }
        Objects.requireNonNull(valorTransacao, "valor da transacao e obrigatorio");
        if (valorTransacao.signum() <= 0) {
            throw new IllegalArgumentException(
                    "valor da transacao deve ser positivo; recebido: " + valorTransacao);
        }
        tipoTransacao = tipoTransacao.trim().toUpperCase();
    }
}
