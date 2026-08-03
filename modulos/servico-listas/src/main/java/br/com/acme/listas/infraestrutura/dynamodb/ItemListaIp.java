package br.com.acme.listas.infraestrutura.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Item da tabela {@code listas_ip}. Unica tabela com TTL nativo habilitado, no atributo
 * {@code expiraEm} — IP e identificador efemero e nao deve bloquear indefinidamente.
 */
@DynamoDbBean
public class ItemListaIp {

    private String ip;
    private ItemPertinencia restritiva;
    private Long expiraEm;

    public ItemListaIp() {
    }

    @DynamoDbPartitionKey
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public ItemPertinencia getRestritiva() {
        return restritiva;
    }

    public void setRestritiva(ItemPertinencia restritiva) {
        this.restritiva = restritiva;
    }

    /**
     * Atributo de TTL, em epoch de segundos. Duplicado no nivel do item porque o TTL do DynamoDB
     * so opera sobre atributo de topo, nao aninhado em mapa.
     */
    public Long getExpiraEm() {
        return expiraEm;
    }

    public void setExpiraEm(Long expiraEm) {
        this.expiraEm = expiraEm;
    }
}
