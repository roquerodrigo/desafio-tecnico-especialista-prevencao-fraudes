package br.com.acme.motordecisao.infraestrutura.persistencia;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "condicao")
public class CondicaoEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regra_id", nullable = false)
    private RegraEntity regra;

    @jakarta.persistence.Column(nullable = false)
    private String campo;

    @jakarta.persistence.Column(nullable = false)
    private String operador;

    @jakarta.persistence.Column(nullable = false)
    private String valor;

    protected CondicaoEntity() {
    }

    public CondicaoEntity(UUID id, String campo, String operador, String valor) {
        this.id = id;
        this.campo = campo;
        this.operador = operador;
        this.valor = valor;
    }

    void associarA(RegraEntity regra) {
        this.regra = regra;
    }

    public UUID getId() {
        return id;
    }

    public String getCampo() {
        return campo;
    }

    public String getOperador() {
        return operador;
    }

    public String getValor() {
        return valor;
    }
}
