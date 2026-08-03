package br.com.acme.motordecisao.dominio.composicao;

import br.com.acme.motordecisao.dominio.regra.Escada;
import br.com.acme.motordecisao.dominio.regra.Regra;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resultado da composicao entre o conjunto padrao e o conjunto de um tipo de transacao.
 *
 * <p>A composicao segue a chave de sobreposicao:
 * <ul>
 *   <li>mesma chave — a regra especifica <b>substitui</b> a padrao</li>
 *   <li>chave inexistente no padrao — a especifica e <b>acrescentada</b></li>
 *   <li>regra padrao sem correspondente — <b>permanece ativa</b></li>
 *   <li>regra sem chave — sempre <b>aditiva</b>, nunca substitui</li>
 * </ul>
 *
 * <p>Complexidade: <b>O(P + E)</b> com {@link LinkedHashMap}, onde P sao as regras padrao e E as
 * especificas. A ordem de insercao e preservada para tornar a saida da API deterministica.
 *
 * <p>E derivado, nunca persistido.
 */
public final class ConjuntoEfetivo {

    public static final String TIPO_PADRAO = "PADRAO";

    private final String tipoTransacao;
    private final List<Regra> regrasCondicionais;
    private final Map<String, Escada> escadas;
    private final Map<String, String> origemPorChave;

    private ConjuntoEfetivo(String tipoTransacao, List<Regra> regrasCondicionais,
                            Map<String, Escada> escadas, Map<String, String> origemPorChave) {
        this.tipoTransacao = tipoTransacao;
        this.regrasCondicionais = List.copyOf(regrasCondicionais);
        this.escadas = Map.copyOf(escadas);
        this.origemPorChave = Map.copyOf(origemPorChave);
    }

    /**
     * Compoe o conjunto efetivo de um tipo a partir do conjunto padrao e do especifico.
     *
     * @param tipoTransacao tipo concreto (nunca {@code PADRAO})
     * @param regrasPadrao regras do conjunto base
     * @param regrasEspecificas regras cadastradas para o tipo; pode ser vazio
     */
    public static ConjuntoEfetivo compor(String tipoTransacao,
                                         Collection<Regra> regrasPadrao,
                                         Collection<Regra> regrasEspecificas) {
        Objects.requireNonNull(tipoTransacao, "tipo de transacao e obrigatorio");

        Map<String, Regra> porChave = new LinkedHashMap<>();
        List<Regra> aditivas = new ArrayList<>();
        Map<String, String> origem = new LinkedHashMap<>();

        acumular(regrasPadrao, porChave, aditivas, origem, TIPO_PADRAO);
        acumular(regrasEspecificas, porChave, aditivas, origem, tipoTransacao.toUpperCase());

        List<Regra> todas = new ArrayList<>(porChave.values());
        todas.addAll(aditivas);

        return new ConjuntoEfetivo(
                tipoTransacao.toUpperCase(),
                todas.stream().filter(regra -> !regra.ehEscada()).toList(),
                construirEscadas(todas),
                origem);
    }

    private static void acumular(Collection<Regra> regras, Map<String, Regra> porChave,
                                 List<Regra> aditivas, Map<String, String> origem, String rotuloOrigem) {
        if (regras == null) {
            return;
        }
        for (Regra regra : regras) {
            if (regra.temChave()) {
                porChave.put(regra.chave(), regra);
                origem.put(regra.chave(), rotuloOrigem);
            } else {
                aditivas.add(regra);
            }
        }
    }

    private static Map<String, Escada> construirEscadas(List<Regra> regras) {
        Map<String, List<Regra>> agrupadas = new LinkedHashMap<>();
        for (Regra regra : regras) {
            if (regra.ehEscada()) {
                agrupadas.computeIfAbsent(regra.escada(), nome -> new ArrayList<>()).add(regra);
            }
        }
        Map<String, Escada> construidas = new LinkedHashMap<>();
        agrupadas.forEach((nome, faixas) -> construidas.put(nome, Escada.construir(nome, faixas)));
        return construidas;
    }

    public String tipoTransacao() {
        return tipoTransacao;
    }

    public List<Regra> regrasCondicionais() {
        return regrasCondicionais;
    }

    public Collection<Escada> escadas() {
        return escadas.values();
    }

    /**
     * De qual conjunto a regra desta chave veio. Torna a sobreposicao observavel pela API.
     */
    public String origemDaChave(String chave) {
        return origemPorChave.get(chave);
    }

    public List<Regra> todasAsRegras() {
        List<Regra> todas = new ArrayList<>(regrasCondicionais);
        escadas.values().forEach(escada -> todas.addAll(escada.faixas()));
        return List.copyOf(todas);
    }
}
