package br.com.acme.listas.infraestrutura.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Item da tabela {@code listas_dispositivo}. Sem TTL: dispositivo comprometido nao deixa de ser
 * comprometido com o tempo.
 */
@DynamoDbBean
public class ItemListaDispositivo {

    private String idDispositivo;
    private ItemPertinencia restritiva;

    public ItemListaDispositivo() {
    }

    @DynamoDbPartitionKey
    public String getIdDispositivo() {
        return idDispositivo;
    }

    public void setIdDispositivo(String idDispositivo) {
        this.idDispositivo = idDispositivo;
    }

    public ItemPertinencia getRestritiva() {
        return restritiva;
    }

    public void setRestritiva(ItemPertinencia restritiva) {
        this.restritiva = restritiva;
    }
}
