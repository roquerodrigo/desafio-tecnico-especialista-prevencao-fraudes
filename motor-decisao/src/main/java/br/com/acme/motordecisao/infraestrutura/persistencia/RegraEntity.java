package br.com.acme.motordecisao.infraestrutura.persistencia;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "regra")
public class RegraEntity {

    @Id
    private UUID id;

    private String chave;

    @Column(name = "tipo_transacao", nullable = false)
    private String tipoTransacao;

    @Column(nullable = false)
    private String descricao;

    private String escada;

    @Column(name = "limite_superior")
    private BigDecimal limiteSuperior;

    @Column(name = "operador_logico")
    private String operadorLogico;

    @Column(name = "acao_tipo", nullable = false)
    private String acaoTipo;

    @Column(name = "acao_pontos", nullable = false)
    private int acaoPontos;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criado_em", nullable = false, updatable = false, insertable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false, insertable = false)
    private Instant atualizadoEm;

    @OneToMany(mappedBy = "regra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<CondicaoEntity> condicoes = new ArrayList<>();

    protected RegraEntity() {
    }

    public RegraEntity(UUID id, String chave, String tipoTransacao, String descricao, String escada,
                       BigDecimal limiteSuperior, String operadorLogico, String acaoTipo, int acaoPontos) {
        this.id = id;
        this.chave = chave;
        this.tipoTransacao = tipoTransacao;
        this.descricao = descricao;
        this.escada = escada;
        this.limiteSuperior = limiteSuperior;
        this.operadorLogico = operadorLogico;
        this.acaoTipo = acaoTipo;
        this.acaoPontos = acaoPontos;
        this.ativa = true;
    }

    public void adicionarCondicao(CondicaoEntity condicao) {
        condicao.associarA(this);
        condicoes.add(condicao);
    }

    public void substituirCondicoes(List<CondicaoEntity> novas) {
        condicoes.clear();
        novas.forEach(this::adicionarCondicao);
    }

    public UUID getId() {
        return id;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getTipoTransacao() {
        return tipoTransacao;
    }

    public void setTipoTransacao(String tipoTransacao) {
        this.tipoTransacao = tipoTransacao;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getEscada() {
        return escada;
    }

    public void setEscada(String escada) {
        this.escada = escada;
    }

    public BigDecimal getLimiteSuperior() {
        return limiteSuperior;
    }

    public void setLimiteSuperior(BigDecimal limiteSuperior) {
        this.limiteSuperior = limiteSuperior;
    }

    public String getOperadorLogico() {
        return operadorLogico;
    }

    public void setOperadorLogico(String operadorLogico) {
        this.operadorLogico = operadorLogico;
    }

    public String getAcaoTipo() {
        return acaoTipo;
    }

    public void setAcaoTipo(String acaoTipo) {
        this.acaoTipo = acaoTipo;
    }

    public int getAcaoPontos() {
        return acaoPontos;
    }

    public void setAcaoPontos(int acaoPontos) {
        this.acaoPontos = acaoPontos;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public List<CondicaoEntity> getCondicoes() {
        return condicoes;
    }
}
