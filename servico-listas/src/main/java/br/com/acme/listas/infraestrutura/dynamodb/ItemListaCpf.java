package br.com.acme.listas.infraestrutura.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Item da tabela {@code listas_cpf}. Unico tipo que admite as duas pertinencias.
 *
 * <p>A partition key e o proprio CPF, o que distribui uniformemente entre particoes.
 * {@code PK = "CPF"} com sort key no valor concentraria todas as consultas de CPF numa unica
 * particao — hot partition exatamente no dado de maior volume.
 */
@DynamoDbBean
public class ItemListaCpf {

    private String cpf;
    private ItemPertinencia permissiva;
    private ItemPertinencia restritiva;

    public ItemListaCpf() {
    }

    @DynamoDbPartitionKey
    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public ItemPertinencia getPermissiva() {
        return permissiva;
    }

    public void setPermissiva(ItemPertinencia permissiva) {
        this.permissiva = permissiva;
    }

    public ItemPertinencia getRestritiva() {
        return restritiva;
    }

    public void setRestritiva(ItemPertinencia restritiva) {
        this.restritiva = restritiva;
    }
}
