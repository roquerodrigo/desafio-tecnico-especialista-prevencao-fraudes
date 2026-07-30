package br.com.acme.motordecisao.aplicacao;

import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraEntity;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraJpaRepository;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de regras. Toda escrita publica o evento de invalidacao, para que todas as replicas
 * recarreguem seu cache.
 */
@Service
public class GerenciarRegrasUseCase {

    private final RegraJpaRepository repositorio;
    private final RegraMapper mapper;
    private final CacheRegras cacheRegras;
    private final NotificadorAlteracaoRegras notificador;

    public GerenciarRegrasUseCase(RegraJpaRepository repositorio, RegraMapper mapper,
                                  CacheRegras cacheRegras, NotificadorAlteracaoRegras notificador) {
        this.repositorio = repositorio;
        this.mapper = mapper;
        this.cacheRegras = cacheRegras;
        this.notificador = notificador;
    }

    @Transactional
    public Regra criar(Regra regra) {
        garantirChaveDisponivel(regra, null);
        RegraEntity salva = repositorio.save(mapper.paraEntidade(regra));
        propagar(regra.tipoTransacao(), OperacaoRegra.CRIACAO);
        return mapper.paraDominio(salva);
    }

    @Transactional
    public Regra atualizar(UUID id, Regra regra) {
        RegraEntity entidade = repositorio.findById(id)
                .orElseThrow(() -> new RegraNaoEncontradaException(id));
        garantirChaveDisponivel(regra, id);
        mapper.atualizarEntidade(entidade, regra);
        RegraEntity salva = repositorio.save(entidade);
        propagar(regra.tipoTransacao(), OperacaoRegra.ALTERACAO);
        return mapper.paraDominio(salva);
    }

    @Transactional
    public void remover(UUID id) {
        RegraEntity entidade = repositorio.findById(id)
                .orElseThrow(() -> new RegraNaoEncontradaException(id));
        String tipoTransacao = entidade.getTipoTransacao();
        repositorio.delete(entidade);
        propagar(tipoTransacao, OperacaoRegra.EXCLUSAO);
    }

    /**
     * Recarrega o cache local e avisa as demais replicas.
     *
     * <p>A recarga local e sincrona de proposito: quem atende a alteracao passa a servi-la
     * imediatamente, sem depender do evento voltar pelo broker. O evento cuida das outras replicas.
     * Se o broker estiver indisponivel, esta instancia continua correta e o erro fica registrado.
     */
    private void propagar(String tipoTransacaoAfetado, OperacaoRegra operacao) {
        cacheRegras.recarregar();
        notificador.notificar(tipoTransacaoAfetado, operacao);
    }

    @Transactional(readOnly = true)
    public Regra buscar(UUID id) {
        return repositorio.findById(id)
                .map(mapper::paraDominio)
                .orElseThrow(() -> new RegraNaoEncontradaException(id));
    }

    @Transactional(readOnly = true)
    public List<Regra> listarCadastradas(String tipoTransacao) {
        List<RegraEntity> entidades = tipoTransacao == null
                ? repositorio.findByAtivaTrue()
                : repositorio.findByTipoTransacaoAndAtivaTrue(tipoTransacao.trim().toUpperCase());
        return entidades.stream().map(mapper::paraDominio).toList();
    }

    /**
     * Conjunto efetivo do tipo, apos a composicao com o conjunto padrao. E o que torna a chave de
     * sobreposicao observavel de fora.
     */
    public Optional<ConjuntoEfetivo> conjuntoEfetivo(String tipoTransacao) {
        return cacheRegras.conjuntoEfetivoDe(tipoTransacao);
    }

    private void garantirChaveDisponivel(Regra regra, UUID idEmAlteracao) {
        if (!regra.temChave()) {
            return;
        }
        repositorio.findByTipoTransacaoAndChave(regra.tipoTransacao(), regra.chave())
                .filter(existente -> !existente.getId().equals(idEmAlteracao))
                .ifPresent(existente -> {
                    throw new ChaveDuplicadaException(regra.chave(), regra.tipoTransacao());
                });
    }

    public enum OperacaoRegra {
        CRIACAO,
        ALTERACAO,
        EXCLUSAO
    }

    /**
     * Porta de saida para a notificacao de alteracao. Mantida como interface para que o caso de
     * uso permaneca testavel sem broker.
     */
    public interface NotificadorAlteracaoRegras {
        void notificar(String tipoTransacaoAfetado, OperacaoRegra operacao);
    }
}
