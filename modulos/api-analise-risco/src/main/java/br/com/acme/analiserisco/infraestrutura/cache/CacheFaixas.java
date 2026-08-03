package br.com.acme.analiserisco.infraestrutura.cache;

import br.com.acme.analiserisco.dominio.FaixaScore;
import br.com.acme.analiserisco.dominio.TabelaFaixas;
import br.com.acme.analiserisco.infraestrutura.persistencia.FaixaScoreEntity;
import br.com.acme.analiserisco.infraestrutura.persistencia.FaixaScoreJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache da tabela de faixas. Terceira aplicacao do mesmo padrao de invalidacao por evento —
 * listas de regras, regras e faixas —, o que mantem um unico conceito de propagacao no sistema.
 *
 * <p>Como no cache de regras, carga invalida <b>preserva o estado anterior</b>. Uma tabela de
 * faixas parcial classificaria scores em faixas erradas, invertendo decisoes de risco
 * silenciosamente.
 */
@Component
public class CacheFaixas {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheFaixas.class);

    private final FaixaScoreJpaRepository repositorio;
    private final AtomicReference<TabelaFaixas> tabela = new AtomicReference<>();

    public CacheFaixas(FaixaScoreJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public boolean recarregar() {
        try {
            List<FaixaScore> faixas = repositorio.findAllByOrderByOrdemAsc().stream()
                    .map(FaixaScoreEntity::paraDominio)
                    .toList();

            tabela.set(TabelaFaixas.construir(faixas));
            LOGGER.info("Cache de faixas de score recarregado: {} faixas", faixas.size());
            return true;
        } catch (RuntimeException excecao) {
            LOGGER.error("Falha ao recarregar as faixas de score. O estado anterior foi preservado "
                    + "e continua sendo servido. Causa: {}", excecao.getMessage(), excecao);
            return false;
        }
    }

    public Optional<TabelaFaixas> tabela() {
        return Optional.ofNullable(tabela.get());
    }

    public boolean carregado() {
        return tabela.get() != null;
    }
}
