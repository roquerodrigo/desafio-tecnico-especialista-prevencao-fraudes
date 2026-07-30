package br.com.acme.analiserisco.aplicacao;

/**
 * Lancada quando a analise nao pode ser concluida por indisponibilidade tecnica.
 *
 * <p>Resulta em {@code 503}, nunca em {@code 200} com {@code NEGADA}. A distincao e de negocio:
 * {@code NEGADA} MUST significar decisao real de risco. Se indisponibilidade virasse negativa, o
 * cliente nao conseguiria distinguir "cliente de risco" de "nosso servico caiu", e um retry
 * legitimo ficaria indistinguivel de insistencia em transacao negada.
 */
public class AnaliseIndisponivelException extends RuntimeException {

    public AnaliseIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
