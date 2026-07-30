package br.com.acme.auditoria.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registro imutavel de uma analise concluida.
 *
 * <p>E o unico lugar do sistema onde score, classificacao, decisao e regras acionadas convivem — e
 * ele nao e exposto ao cliente da analise. O CPF fica em claro por decisao explicita: investigacao
 * de fraude e contestacao exigem o dado real, com acesso controlado.
 */
public record TrilhaDecisao(
        UUID id,
        String idCorrelacao,
        String cpf,
        String ip,
        String idDispositivo,
        String tipoTransacao,
        BigDecimal valorTransacao,
        int score,
        String classificacao,
        String decisao,
        boolean consultaListasDegradada,
        List<RegraAcionada> regrasAcionadas,
        ResultadoListas resultadoListas,
        Instant ocorridoEm) {

    public record RegraAcionada(String chave, String descricao, String acao, int pontos) {
    }

    public record ResultadoListas(
            boolean cpfEmListaPermissiva,
            boolean cpfEmListaRestritiva,
            boolean ipEmListaRestritiva,
            boolean dispositivoEmListaRestritiva) {
    }
}
