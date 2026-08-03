package br.com.acme.analiserisco.dominio;

/**
 * Sinais de lista consumidos pelo motor.
 *
 * <p>{@code degradado} registra que a consulta nao pode ser feita e os sinais foram assumidos como
 * falsos. Vai para a trilha de auditoria — nunca para a resposta ao cliente. Sem esse marcador, a
 * degradacao seria silenciosa e nao haveria como medir quanto risco a indisponibilidade gerou.
 */
public record ResultadoListas(
        boolean cpfEmListaPermissiva,
        boolean cpfEmListaRestritiva,
        boolean ipEmListaRestritiva,
        boolean dispositivoEmListaRestritiva,
        boolean degradado) {

    public static ResultadoListas de(boolean cpfPermissiva, boolean cpfRestritiva,
                                     boolean ipRestritiva, boolean dispositivoRestritiva) {
        return new ResultadoListas(cpfPermissiva, cpfRestritiva, ipRestritiva, dispositivoRestritiva, false);
    }

    /**
     * Estado assumido quando o servico de listas esta indisponivel: nada consta em lista alguma.
     *
     * <p>A transacao continua sendo avaliada pelas regras de valor. O sinal de lista e desejavel,
     * nao essencial — diferente do score, cuja ausencia impede qualquer decisao.
     */
    public static ResultadoListas semConsulta() {
        return new ResultadoListas(false, false, false, false, true);
    }
}
