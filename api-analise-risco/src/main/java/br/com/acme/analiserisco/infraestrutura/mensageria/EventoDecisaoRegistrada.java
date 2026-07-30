package br.com.acme.analiserisco.infraestrutura.mensageria;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Trilha de uma analise concluida.
 *
 * <p>Este e o unico lugar do sistema onde score, classificacao e regras acionadas trafegam juntos —
 * e ele nao e exposto ao cliente. CPF vai em claro por decisao explicita: a trilha e base de
 * investigacao e de defesa em contestacao. O topico e interno; o mascaramento aplica-se a log.
 */
public record EventoDecisaoRegistrada(
        UUID idEvento,
        String idCorrelacao,
        Instant ocorridoEm,
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
        ResultadoListas resultadoListas) {

    public record RegraAcionada(String chave, String descricao, String acao, int pontos) {
    }

    public record ResultadoListas(
            boolean cpfEmListaPermissiva,
            boolean cpfEmListaRestritiva,
            boolean ipEmListaRestritiva,
            boolean dispositivoEmListaRestritiva) {
    }
}
