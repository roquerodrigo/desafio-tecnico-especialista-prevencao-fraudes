package br.com.acme.listas.infraestrutura.dynamodb;

import br.com.acme.listas.dominio.Pertinencia;
import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Mapa aninhado que representa uma pertinencia no DynamoDB.
 *
 * <p>A presenca do mapa significa que a variavel consta na lista — nao existe flag booleana
 * redundante, o que torna inexpressavel o estado incoerente "nao consta mas tem idLista".
 */
@DynamoDbBean
public class ItemPertinencia {

    private String idLista;
    private String situacao;
    private Long dataInclusao;
    private Long expiraEm;

    public ItemPertinencia() {
    }

    public static ItemPertinencia de(Pertinencia pertinencia) {
        if (pertinencia == null) {
            return null;
        }
        ItemPertinencia item = new ItemPertinencia();
        item.idLista = pertinencia.idLista();
        item.situacao = pertinencia.situacao().name();
        item.dataInclusao = pertinencia.dataInclusao() == null
                ? Instant.now().getEpochSecond()
                : pertinencia.dataInclusao().getEpochSecond();
        item.expiraEm = pertinencia.expiraEm() == null ? null : pertinencia.expiraEm().getEpochSecond();
        return item;
    }

    public Pertinencia paraDominio() {
        return new Pertinencia(
                idLista,
                Pertinencia.Situacao.valueOf(situacao),
                dataInclusao == null ? null : Instant.ofEpochSecond(dataInclusao),
                expiraEm == null ? null : Instant.ofEpochSecond(expiraEm));
    }

    public String getIdLista() {
        return idLista;
    }

    public void setIdLista(String idLista) {
        this.idLista = idLista;
    }

    public String getSituacao() {
        return situacao;
    }

    public void setSituacao(String situacao) {
        this.situacao = situacao;
    }

    public Long getDataInclusao() {
        return dataInclusao;
    }

    public void setDataInclusao(Long dataInclusao) {
        this.dataInclusao = dataInclusao;
    }

    public Long getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(Long expiraEm) {
        this.expiraEm = expiraEm;
    }
}
