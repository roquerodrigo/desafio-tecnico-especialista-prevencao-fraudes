package br.com.acme.motordecisao.dominio.regra;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Regra de decisao. Assume exatamente uma de duas naturezas, mutuamente exclusivas.
 *
 * <p><b>Escada</b> — declara {@code escada} e {@code limiteSuperior}. Regras da mesma escada sao
 * mutuamente exclusivas: exatamente uma se aplica a cada transacao. O limite inferior nao e
 * declarado; ele e o teto da faixa anterior. Isso torna lacuna e sobreposicao inexpressaveis,
 * qualquer que seja a composicao entre conjunto padrao e especifico.
 *
 * <p><b>Condicional</b> — declara {@code condicoes} combinadas por {@code operadorLogico}.
 * Regras condicionais sao cumulativas: todas as que casam aplicam sua acao.
 *
 * <p>A {@code chave} identifica o predicado avaliado e e o que permite compor os dois conjuntos.
 * Regra sem chave e sempre aditiva e nunca substitui outra.
 */
public final class Regra {

    private final UUID id;
    private final String chave;
    private final String tipoTransacao;
    private final String descricao;
    private final String escada;
    private final BigDecimal limiteSuperior;
    private final OperadorLogico operadorLogico;
    private final List<Condicao> condicoes;
    private final Acao acao;

    private Regra(UUID id, String chave, String tipoTransacao, String descricao, String escada,
                  BigDecimal limiteSuperior, OperadorLogico operadorLogico,
                  List<Condicao> condicoes, Acao acao) {
        this.id = Objects.requireNonNull(id, "id da regra e obrigatorio");
        this.chave = normalizarChave(chave);
        this.tipoTransacao = exigirTipoTransacao(tipoTransacao);
        this.descricao = Objects.requireNonNull(descricao, "descricao da regra e obrigatoria");
        this.escada = normalizarChave(escada);
        this.limiteSuperior = limiteSuperior;
        this.operadorLogico = operadorLogico;
        this.condicoes = condicoes == null ? List.of() : List.copyOf(condicoes);
        this.acao = Objects.requireNonNull(acao, "acao da regra e obrigatoria");
        validarNatureza();
    }

    public static Regra deEscada(UUID id, String chave, String tipoTransacao, String descricao,
                                 String escada, BigDecimal limiteSuperior, Acao acao) {
        return new Regra(id, chave, tipoTransacao, descricao, escada, limiteSuperior,
                null, List.of(), acao);
    }

    public static Regra condicional(UUID id, String chave, String tipoTransacao, String descricao,
                                    OperadorLogico operadorLogico, List<Condicao> condicoes, Acao acao) {
        return new Regra(id, chave, tipoTransacao, descricao, null, null,
                operadorLogico, condicoes, acao);
    }

    /**
     * Reconstitui uma regra a partir da persistencia, deduzindo a natureza pelo campo
     * {@code escada}. Passa pelas mesmas invariantes da construcao: dado corrompido no banco
     * falha na carga, nao no meio de uma avaliacao.
     */
    public static Regra reconstituir(UUID id, String chave, String tipoTransacao, String descricao,
                                     String escada, BigDecimal limiteSuperior,
                                     OperadorLogico operadorLogico, List<Condicao> condicoes, Acao acao) {
        return new Regra(id, chave, tipoTransacao, descricao, escada, limiteSuperior,
                operadorLogico, condicoes, acao);
    }

    private void validarNatureza() {
        boolean temEscada = escada != null;
        boolean temCondicoes = !condicoes.isEmpty();

        if (temEscada && temCondicoes) {
            throw new NaturezaRegraInvalidaException(
                    "Regra de escada nao pode declarar condicoes. Regra: " + descricao);
        }
        if (temEscada && operadorLogico != null) {
            throw new NaturezaRegraInvalidaException(
                    "Regra de escada nao pode declarar operador logico. Regra: " + descricao);
        }
        if (!temEscada && !temCondicoes) {
            throw new NaturezaRegraInvalidaException(
                    "Regra condicional exige ao menos uma condicao. Regra: " + descricao);
        }
        if (!temEscada && limiteSuperior != null) {
            throw new NaturezaRegraInvalidaException(
                    "Limite superior so tem sentido em regra de escada. Regra: " + descricao);
        }
        if (temEscada && limiteSuperior != null && limiteSuperior.signum() <= 0) {
            throw new NaturezaRegraInvalidaException(
                    "Limite superior deve ser positivo. Regra: " + descricao);
        }
    }

    private static String normalizarChave(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String exigirTipoTransacao(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("tipo de transacao e obrigatorio");
        }
        return valor.trim().toUpperCase();
    }

    public boolean ehEscada() {
        return escada != null;
    }

    public boolean semTeto() {
        return ehEscada() && limiteSuperior == null;
    }

    public boolean temChave() {
        return chave != null;
    }

    public UUID id() {
        return id;
    }

    public String chave() {
        return chave;
    }

    public String tipoTransacao() {
        return tipoTransacao;
    }

    public String descricao() {
        return descricao;
    }

    public String escada() {
        return escada;
    }

    public BigDecimal limiteSuperior() {
        return limiteSuperior;
    }

    public OperadorLogico operadorLogico() {
        return OperadorLogico.ouPadrao(operadorLogico);
    }

    public List<Condicao> condicoes() {
        return condicoes;
    }

    public Acao acao() {
        return acao;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        return outro instanceof Regra regra && id.equals(regra.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Regra[" + (chave != null ? chave : "sem-chave") + "@" + tipoTransacao + "]";
    }
}
