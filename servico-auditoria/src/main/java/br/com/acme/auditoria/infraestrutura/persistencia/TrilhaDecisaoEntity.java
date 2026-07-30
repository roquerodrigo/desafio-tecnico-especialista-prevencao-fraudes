package br.com.acme.auditoria.infraestrutura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trilha_decisao")
public class TrilhaDecisaoEntity {

    @Id
    private UUID id;

    @Column(name = "id_correlacao", nullable = false, unique = true)
    private String idCorrelacao;

    @Column(nullable = false)
    private String cpf;

    @Column(nullable = false)
    private String ip;

    @Column(name = "id_dispositivo", nullable = false)
    private String idDispositivo;

    @Column(name = "tipo_transacao", nullable = false)
    private String tipoTransacao;

    @Column(name = "valor_transacao", nullable = false)
    private BigDecimal valorTransacao;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String classificacao;

    @Column(nullable = false)
    private String decisao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "regras_acionadas", nullable = false)
    private String regrasAcionadas;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado_listas", nullable = false)
    private String resultadoListas;

    @Column(name = "consulta_degradada", nullable = false)
    private boolean consultaDegradada;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    @Column(name = "registrado_em", nullable = false, insertable = false, updatable = false)
    private Instant registradoEm;

    protected TrilhaDecisaoEntity() {
    }

    public TrilhaDecisaoEntity(UUID id, String idCorrelacao, String cpf, String ip, String idDispositivo,
                               String tipoTransacao, BigDecimal valorTransacao, int score,
                               String classificacao, String decisao, String regrasAcionadas,
                               String resultadoListas, boolean consultaDegradada, Instant ocorridoEm) {
        this.id = id;
        this.idCorrelacao = idCorrelacao;
        this.cpf = cpf;
        this.ip = ip;
        this.idDispositivo = idDispositivo;
        this.tipoTransacao = tipoTransacao;
        this.valorTransacao = valorTransacao;
        this.score = score;
        this.classificacao = classificacao;
        this.decisao = decisao;
        this.regrasAcionadas = regrasAcionadas;
        this.resultadoListas = resultadoListas;
        this.consultaDegradada = consultaDegradada;
        this.ocorridoEm = ocorridoEm;
    }

    public UUID getId() {
        return id;
    }

    public String getIdCorrelacao() {
        return idCorrelacao;
    }

    public String getCpf() {
        return cpf;
    }

    public String getIp() {
        return ip;
    }

    public String getIdDispositivo() {
        return idDispositivo;
    }

    public String getTipoTransacao() {
        return tipoTransacao;
    }

    public BigDecimal getValorTransacao() {
        return valorTransacao;
    }

    public int getScore() {
        return score;
    }

    public String getClassificacao() {
        return classificacao;
    }

    public String getDecisao() {
        return decisao;
    }

    public String getRegrasAcionadas() {
        return regrasAcionadas;
    }

    public String getResultadoListas() {
        return resultadoListas;
    }

    public boolean isConsultaDegradada() {
        return consultaDegradada;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    public Instant getRegistradoEm() {
        return registradoEm;
    }
}
