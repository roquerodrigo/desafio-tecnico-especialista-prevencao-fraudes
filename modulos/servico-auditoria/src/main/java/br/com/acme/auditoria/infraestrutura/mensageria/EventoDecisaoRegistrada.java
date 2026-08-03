package br.com.acme.auditoria.infraestrutura.mensageria;

import br.com.acme.auditoria.dominio.TrilhaDecisao;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contrato do evento consumido.
 *
 * <p>Duplicado deliberadamente em relacao ao produtor: nao existe modulo compartilhado entre os
 * servicos. Compartilhar o record acoplaria o ciclo de release dos dois — mudar o evento obrigaria
 * a recompilar e reimplantar ambos em conjunto, o que contraria a evolucao independente. A traducao
 * para o dominio acontece aqui (ACL).
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

    public TrilhaDecisao paraDominio() {
        return new TrilhaDecisao(
                UUID.randomUUID(),
                idCorrelacao,
                cpf,
                ip,
                idDispositivo,
                tipoTransacao,
                valorTransacao,
                score,
                classificacao,
                decisao,
                consultaListasDegradada,
                regrasAcionadas == null ? List.of() : regrasAcionadas.stream()
                        .map(regra -> new TrilhaDecisao.RegraAcionada(
                                regra.chave(), regra.descricao(), regra.acao(), regra.pontos()))
                        .toList(),
                resultadoListas == null
                        ? new TrilhaDecisao.ResultadoListas(false, false, false, false)
                        : new TrilhaDecisao.ResultadoListas(
                                resultadoListas.cpfEmListaPermissiva(),
                                resultadoListas.cpfEmListaRestritiva(),
                                resultadoListas.ipEmListaRestritiva(),
                                resultadoListas.dispositivoEmListaRestritiva()),
                ocorridoEm);
    }
}
