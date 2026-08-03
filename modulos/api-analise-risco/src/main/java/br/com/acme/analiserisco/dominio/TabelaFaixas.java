package br.com.acme.analiserisco.dominio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Faixas de classificacao de risco, resolvidas por limite superior.
 *
 * <p>Mesma estrutura da escada de valor do motor: {@link TreeMap#ceilingEntry} em <b>O(log f)</b>,
 * com a faixa sem teto como fallback. O padrao de escada aparece nos dois lugares porque o
 * problema e o mesmo — faixas contiguas e mutuamente exclusivas sobre uma grandeza ordenada.
 *
 * <p>Declarar apenas o teto elimina lacuna e sobreposicao por construcao: nao existe configuracao
 * em que um score possivel fique sem faixa, nem em que dois pesos se apliquem ao mesmo score.
 */
public final class TabelaFaixas {

    private final NavigableMap<Integer, FaixaScore> faixasComTeto;
    private final FaixaScore faixaSemTeto;

    private TabelaFaixas(NavigableMap<Integer, FaixaScore> faixasComTeto, FaixaScore faixaSemTeto) {
        this.faixasComTeto = faixasComTeto;
        this.faixaSemTeto = faixaSemTeto;
    }

    public static TabelaFaixas construir(Collection<FaixaScore> faixas) {
        if (faixas == null || faixas.isEmpty()) {
            throw new TabelaFaixasInvalidaException("E obrigatoria ao menos uma faixa de score");
        }

        NavigableMap<Integer, FaixaScore> comTeto = new TreeMap<>();
        FaixaScore semTeto = null;
        Set<ClassificacaoRisco> classificacoesVistas = new HashSet<>();

        for (FaixaScore faixa : faixas) {
            if (!classificacoesVistas.add(faixa.classificacao())) {
                throw new TabelaFaixasInvalidaException(
                        "Classificacao duplicada na tabela de faixas: " + faixa.classificacao());
            }
            if (faixa.semTeto()) {
                if (semTeto != null) {
                    throw new TabelaFaixasInvalidaException(
                            "E obrigatoria exatamente uma faixa sem limite superior; foram encontradas 2 ("
                                    + semTeto.classificacao() + " e " + faixa.classificacao() + ")");
                }
                semTeto = faixa;
                continue;
            }
            if (comTeto.putIfAbsent(faixa.limiteSuperior(), faixa) != null) {
                throw new TabelaFaixasInvalidaException(
                        "Limite superior duplicado na tabela de faixas: " + faixa.limiteSuperior());
            }
        }

        if (semTeto == null) {
            throw new TabelaFaixasInvalidaException(
                    "E obrigatoria exatamente uma faixa sem limite superior; foram encontradas 0. "
                            + "Sem ela, scores acima do maior teto ficariam sem classificacao.");
        }

        return new TabelaFaixas(comTeto, semTeto);
    }

    /**
     * Faixa aplicavel ao score. Sempre exatamente uma. Complexidade: <b>O(log f)</b>.
     */
    public FaixaScore classificar(int score) {
        return Optional.ofNullable(faixasComTeto.ceilingEntry(score))
                .map(Map.Entry::getValue)
                .orElse(faixaSemTeto);
    }

    /**
     * Faixas em ordem crescente de limite, com a faixa sem teto ao final.
     */
    public List<FaixaScore> faixas() {
        List<FaixaScore> todas = new ArrayList<>(faixasComTeto.values());
        todas.add(faixaSemTeto);
        return List.copyOf(todas);
    }
}
