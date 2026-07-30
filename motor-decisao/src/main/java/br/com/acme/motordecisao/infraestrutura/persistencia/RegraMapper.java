package br.com.acme.motordecisao.infraestrutura.persistencia;

import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.Operador;
import br.com.acme.motordecisao.dominio.regra.OperadorLogico;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.dominio.regra.TipoAcao;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduz entre entidade de persistencia e agregado de dominio.
 *
 * <p>A traducao para dominio passa pelas invariantes de {@link Regra}: dado corrompido no banco
 * falha na carga do cache, e nao no meio da avaliacao de uma transacao.
 */
@Component
public class RegraMapper {

    public Regra paraDominio(RegraEntity entidade) {
        return Regra.reconstituir(
                entidade.getId(),
                entidade.getChave(),
                entidade.getTipoTransacao(),
                entidade.getDescricao(),
                entidade.getEscada(),
                entidade.getLimiteSuperior(),
                entidade.getOperadorLogico() == null ? null : OperadorLogico.valueOf(entidade.getOperadorLogico()),
                entidade.getCondicoes().stream().map(this::paraDominio).toList(),
                new Acao(TipoAcao.valueOf(entidade.getAcaoTipo()), entidade.getAcaoPontos()));
    }

    private Condicao paraDominio(CondicaoEntity entidade) {
        return new Condicao(
                Campo.valueOf(entidade.getCampo()),
                Operador.valueOf(entidade.getOperador()),
                entidade.getValor());
    }

    public RegraEntity paraEntidade(Regra regra) {
        RegraEntity entidade = new RegraEntity(
                regra.id(),
                regra.chave(),
                regra.tipoTransacao(),
                regra.descricao(),
                regra.escada(),
                regra.limiteSuperior(),
                regra.ehEscada() ? null : regra.operadorLogico().name(),
                regra.acao().tipo().name(),
                regra.acao().pontos());
        entidade.substituirCondicoes(paraEntidades(regra.condicoes()));
        return entidade;
    }

    public void atualizarEntidade(RegraEntity entidade, Regra regra) {
        entidade.setChave(regra.chave());
        entidade.setTipoTransacao(regra.tipoTransacao());
        entidade.setDescricao(regra.descricao());
        entidade.setEscada(regra.escada());
        entidade.setLimiteSuperior(regra.limiteSuperior());
        entidade.setOperadorLogico(regra.ehEscada() ? null : regra.operadorLogico().name());
        entidade.setAcaoTipo(regra.acao().tipo().name());
        entidade.setAcaoPontos(regra.acao().pontos());
        entidade.substituirCondicoes(paraEntidades(regra.condicoes()));
    }

    private List<CondicaoEntity> paraEntidades(List<Condicao> condicoes) {
        return condicoes.stream()
                .map(condicao -> new CondicaoEntity(UUID.randomUUID(),
                        condicao.campo().name(), condicao.operador().name(), condicao.valor()))
                .toList();
    }
}
