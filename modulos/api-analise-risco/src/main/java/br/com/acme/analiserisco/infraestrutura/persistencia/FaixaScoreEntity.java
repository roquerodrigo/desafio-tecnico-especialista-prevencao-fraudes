package br.com.acme.analiserisco.infraestrutura.persistencia;

import br.com.acme.analiserisco.dominio.ClassificacaoRisco;
import br.com.acme.analiserisco.dominio.Decisao;
import br.com.acme.analiserisco.dominio.FaixaScore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "faixa_score")
public class FaixaScoreEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String classificacao;

    @Column(name = "limite_superior")
    private Integer limiteSuperior;

    @Column(nullable = false)
    private String decisao;

    @Column(nullable = false, unique = true)
    private int ordem;

    protected FaixaScoreEntity() {
    }

    public FaixaScoreEntity(UUID id, String classificacao, Integer limiteSuperior, String decisao, int ordem) {
        this.id = id;
        this.classificacao = classificacao;
        this.limiteSuperior = limiteSuperior;
        this.decisao = decisao;
        this.ordem = ordem;
    }

    public FaixaScore paraDominio() {
        return new FaixaScore(
                ClassificacaoRisco.valueOf(classificacao),
                limiteSuperior,
                Decisao.valueOf(decisao));
    }

    public static FaixaScoreEntity de(FaixaScore faixa, int ordem) {
        return new FaixaScoreEntity(UUID.randomUUID(), faixa.classificacao().name(),
                faixa.limiteSuperior(), faixa.decisao().name(), ordem);
    }

    public UUID getId() {
        return id;
    }

    public int getOrdem() {
        return ordem;
    }
}
