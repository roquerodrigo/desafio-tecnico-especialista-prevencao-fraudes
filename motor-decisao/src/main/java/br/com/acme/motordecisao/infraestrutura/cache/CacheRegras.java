package br.com.acme.motordecisao.infraestrutura.cache;

import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraJpaRepository;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache em memoria dos conjuntos efetivos, por tipo de transacao.
 *
 * <p>Regras sao dado de baixo volume lido integralmente a cada transacao — perfil oposto ao das
 * listas. Por isso ficam em memoria, com invalidacao por evento, e a leitura no caminho critico
 * custa <b>O(1)</b> sem I/O.
 *
 * <p><b>Nunca serve cache vazio ou parcial.</b> Se uma recarga falhar — por invariante de escada
 * violada, por exemplo —, o estado anterior e preservado e o erro registrado. Servir configuracao
 * incompleta produziria score errado silenciosamente, que em antifraude significa aprovar
 * transacao que deveria ser negada.
 *
 * <p>A troca do estado e atomica via {@link AtomicReference}: leitores concorrentes veem o mapa
 * antigo ou o novo por inteiro, nunca um estado intermediario.
 */
@Component
public class CacheRegras {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheRegras.class);

    private final RegraJpaRepository repositorio;
    private final RegraMapper mapper;
    private final AtomicReference<Map<String, ConjuntoEfetivo>> conjuntos = new AtomicReference<>();
    private final AtomicReference<List<Regra>> regrasPadrao = new AtomicReference<>(List.of());

    public CacheRegras(RegraJpaRepository repositorio, RegraMapper mapper) {
        this.repositorio = repositorio;
        this.mapper = mapper;
    }

    /**
     * Recarrega o cache a partir do banco.
     *
     * @return {@code true} se o novo estado foi aplicado; {@code false} se a carga falhou e o
     *         estado anterior foi preservado
     */
    @Transactional(readOnly = true)
    public boolean recarregar() {
        try {
            List<Regra> todas = repositorio.findByAtivaTrue().stream()
                    .map(mapper::paraDominio)
                    .toList();

            Map<String, List<Regra>> porTipo = agruparPorTipo(todas);
            List<Regra> padrao = porTipo.getOrDefault(ConjuntoEfetivo.TIPO_PADRAO, List.of());

            Map<String, ConjuntoEfetivo> novos = new LinkedHashMap<>();
            for (String tipo : porTipo.keySet()) {
                if (ConjuntoEfetivo.TIPO_PADRAO.equals(tipo)) {
                    continue;
                }
                novos.put(tipo, ConjuntoEfetivo.compor(tipo, padrao, porTipo.get(tipo)));
            }

            conjuntos.set(Map.copyOf(novos));
            regrasPadrao.set(padrao);
            LOGGER.info("Cache de regras recarregado: {} regras ativas, {} tipos com conjunto proprio",
                    todas.size(), novos.size());
            return true;
        } catch (RuntimeException excecao) {
            LOGGER.error("Falha ao recarregar o cache de regras. O estado anterior foi preservado "
                    + "e continua sendo servido. Causa: {}", excecao.getMessage(), excecao);
            return false;
        }
    }

    private Map<String, List<Regra>> agruparPorTipo(List<Regra> regras) {
        Map<String, List<Regra>> porTipo = new HashMap<>();
        for (Regra regra : regras) {
            porTipo.computeIfAbsent(regra.tipoTransacao(), tipo -> new ArrayList<>()).add(regra);
        }
        return porTipo;
    }

    /**
     * Conjunto efetivo do tipo. Tipo sem regras proprias recebe apenas o conjunto padrao —
     * composto sob demanda, porque o universo de tipos e aberto.
     */
    public Optional<ConjuntoEfetivo> conjuntoEfetivoDe(String tipoTransacao) {
        Map<String, ConjuntoEfetivo> atual = conjuntos.get();
        if (atual == null) {
            return Optional.empty();
        }
        String tipo = tipoTransacao.trim().toUpperCase();
        ConjuntoEfetivo especifico = atual.get(tipo);
        if (especifico != null) {
            return Optional.of(especifico);
        }
        List<Regra> padrao = regrasPadrao.get();
        if (padrao.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ConjuntoEfetivo.compor(tipo, padrao, List.of()));
    }

    public boolean carregado() {
        return conjuntos.get() != null && !regrasPadrao.get().isEmpty();
    }
}
