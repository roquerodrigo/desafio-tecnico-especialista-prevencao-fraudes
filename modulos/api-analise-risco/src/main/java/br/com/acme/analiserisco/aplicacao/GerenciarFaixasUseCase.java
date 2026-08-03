package br.com.acme.analiserisco.aplicacao;

import br.com.acme.analiserisco.dominio.FaixaScore;
import br.com.acme.analiserisco.dominio.TabelaFaixas;
import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import br.com.acme.analiserisco.infraestrutura.persistencia.FaixaScoreEntity;
import br.com.acme.analiserisco.infraestrutura.persistencia.FaixaScoreJpaRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta e substituicao da tabela de faixas.
 *
 * <p>A substituicao e sempre <b>integral</b>, nunca parcial. A tabela e um conjunto com invariantes
 * entre os elementos — exatamente uma faixa sem teto, limites distintos —, e aceitar alteracao de
 * uma faixa isolada permitiria um estado intermediario invalido.
 */
@Service
public class GerenciarFaixasUseCase {

    private final FaixaScoreJpaRepository repositorio;
    private final CacheFaixas cacheFaixas;
    private final NotificadorAlteracaoFaixas notificador;

    public GerenciarFaixasUseCase(FaixaScoreJpaRepository repositorio, CacheFaixas cacheFaixas,
                                  NotificadorAlteracaoFaixas notificador) {
        this.repositorio = repositorio;
        this.cacheFaixas = cacheFaixas;
        this.notificador = notificador;
    }

    @Transactional(readOnly = true)
    public List<FaixaScore> consultar() {
        return cacheFaixas.tabela()
                .map(TabelaFaixas::faixas)
                .orElseGet(() -> repositorio.findAllByOrderByOrdemAsc().stream()
                        .map(FaixaScoreEntity::paraDominio)
                        .toList());
    }

    @Transactional
    public List<FaixaScore> substituir(List<FaixaScore> novasFaixas) {
        // Valida antes de tocar no banco: TabelaFaixas lanca se as invariantes forem violadas,
        // e a transacao nunca chega a apagar a configuracao vigente.
        TabelaFaixas validada = TabelaFaixas.construir(novasFaixas);

        repositorio.deleteAll();
        repositorio.flush();

        List<FaixaScore> ordenadas = validada.faixas();
        for (int indice = 0; indice < ordenadas.size(); indice++) {
            repositorio.save(FaixaScoreEntity.de(ordenadas.get(indice), indice + 1));
        }

        cacheFaixas.recarregar();
        notificador.notificar();

        return ordenadas;
    }

    /**
     * Porta de saida para a notificacao. Interface para manter o caso de uso testavel sem broker.
     */
    public interface NotificadorAlteracaoFaixas {
        void notificar();
    }
}
