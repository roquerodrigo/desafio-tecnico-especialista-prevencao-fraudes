package br.com.acme.motordecisao.dominio.regra;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Conjunto de faixas mutuamente exclusivas, resolvido por limite superior.
 *
 * <p>A resolucao usa {@link TreeMap#ceilingEntry} — a menor chave maior ou igual ao valor — o que
 * e exatamente a semantica de "primeira faixa cujo teto cobre o valor", em <b>O(log n)</b>. A
 * faixa sem teto fica fora do mapa e serve de fallback quando nenhuma chave cobre.
 *
 * <p>As chaves sao {@link BigDecimal} e nunca {@code double}: a comparacao ocorre em fronteira
 * monetaria, onde erro de representacao binaria mudaria a faixa e, com ela, a pontuacao.
 *
 * <p>As invariantes sao verificadas na construcao, que acontece na carga do cache. Configuracao
 * quebrada falha ali, e nao no meio da transacao de um cliente.
 */
public final class Escada {

    private final String nome;
    private final NavigableMap<BigDecimal, Regra> faixasComTeto;
    private final Regra faixaSemTeto;

    private Escada(String nome, NavigableMap<BigDecimal, Regra> faixasComTeto, Regra faixaSemTeto) {
        this.nome = nome;
        this.faixasComTeto = faixasComTeto;
        this.faixaSemTeto = faixaSemTeto;
    }

    public static Escada construir(String nome, Collection<Regra> regras) {
        Objects.requireNonNull(nome, "nome da escada e obrigatorio");
        if (regras == null || regras.isEmpty()) {
            throw new EscadaInvalidaException("Escada '" + nome + "' nao possui nenhuma faixa");
        }

        NavigableMap<BigDecimal, Regra> comTeto = new TreeMap<>();
        Regra semTeto = null;
        Set<BigDecimal> limitesVistos = new HashSet<>();

        for (Regra regra : regras) {
            if (!regra.ehEscada() || !nome.equals(regra.escada())) {
                throw new EscadaInvalidaException(
                        "Regra " + regra + " nao pertence a escada '" + nome + "'");
            }
            if (regra.semTeto()) {
                if (semTeto != null) {
                    throw new EscadaInvalidaException(
                            "Escada '" + nome + "' possui mais de uma faixa sem limite superior: "
                                    + semTeto + " e " + regra);
                }
                semTeto = regra;
                continue;
            }
            BigDecimal limite = regra.limiteSuperior().stripTrailingZeros();
            if (!limitesVistos.add(limite)) {
                throw new EscadaInvalidaException(
                        "Escada '" + nome + "' possui limite superior duplicado: " + limite);
            }
            comTeto.put(limite, regra);
        }

        if (semTeto == null) {
            throw new EscadaInvalidaException(
                    "Escada '" + nome + "' exige exatamente uma faixa sem limite superior; nenhuma foi declarada. "
                            + "Sem ela, valores acima do maior teto nao seriam pontuados.");
        }

        return new Escada(nome, comTeto, semTeto);
    }

    /**
     * Faixa aplicavel ao valor. Exatamente uma, sempre — a estrutura garante a totalidade.
     *
     * <p>Complexidade: <b>O(log n)</b>.
     */
    public Regra resolver(BigDecimal valor) {
        Objects.requireNonNull(valor, "valor e obrigatorio para resolver a faixa");
        return Optional.ofNullable(faixasComTeto.ceilingEntry(valor.stripTrailingZeros()))
                .map(java.util.Map.Entry::getValue)
                .orElse(faixaSemTeto);
    }

    public String nome() {
        return nome;
    }

    public int quantidadeFaixas() {
        return faixasComTeto.size() + 1;
    }

    public List<Regra> faixas() {
        List<Regra> todas = new java.util.ArrayList<>(faixasComTeto.values());
        todas.add(faixaSemTeto);
        return List.copyOf(todas);
    }
}
