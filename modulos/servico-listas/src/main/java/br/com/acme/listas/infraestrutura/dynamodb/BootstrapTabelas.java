package br.com.acme.listas.infraestrutura.dynamodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveSpecification;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;

/**
 * Cria as tabelas se ainda nao existirem. Idempotente.
 *
 * <p>Ativado por propriedade e destinado a desenvolvimento e teste. Em producao, a infraestrutura
 * seria provisionada por IaC e esta flag ficaria desligada — premissa registrada no README. O ganho
 * de ter isso em codigo e que o mesmo caminho de criacao serve o Docker Compose e o Testcontainers,
 * sem duplicar a definicao do schema.
 */
@Component
public class BootstrapTabelas {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapTabelas.class);

    private final DynamoDbClient cliente;
    private final boolean habilitado;

    public BootstrapTabelas(DynamoDbClient cliente,
                            @Value("${app.dynamodb.criar-tabelas:false}") boolean habilitado) {
        this.cliente = cliente;
        this.habilitado = habilitado;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void criarSeNecessario() {
        if (!habilitado) {
            LOGGER.info("Criacao automatica de tabelas desabilitada; assumindo provisionamento externo");
            return;
        }
        criarTabela(NomesTabelas.LISTAS_CPF, "cpf", false);
        criarTabela(NomesTabelas.LISTAS_IP, "ip", true);
        criarTabela(NomesTabelas.LISTAS_DISPOSITIVO, "idDispositivo", false);
    }

    private void criarTabela(String nome, String chaveParticao, boolean comTtl) {
        if (existe(nome)) {
            LOGGER.info("Tabela {} ja existe", nome);
            return;
        }

        cliente.createTable(CreateTableRequest.builder()
                .tableName(nome)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(KeySchemaElement.builder()
                        .attributeName(chaveParticao)
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(chaveParticao)
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .build());

        cliente.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(nome).build());
        LOGGER.info("Tabela {} criada com chave de particao '{}'", nome, chaveParticao);

        if (comTtl) {
            habilitarTtl(nome);
        }
    }

    /**
     * O DynamoDB Local aceita a chamada de TTL mas nao expurga itens. Isso e irrelevante para a
     * correcao: a aplicacao filtra expiracao na leitura de qualquer forma, como a propria AWS
     * recomenda — o expurgo real pode levar dias mesmo em producao.
     */
    private void habilitarTtl(String nome) {
        try {
            cliente.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(nome)
                    .timeToLiveSpecification(TimeToLiveSpecification.builder()
                            .enabled(true)
                            .attributeName(NomesTabelas.ATRIBUTO_TTL)
                            .build())
                    .build());
            LOGGER.info("TTL habilitado em {} sobre o atributo {}", nome, NomesTabelas.ATRIBUTO_TTL);
        } catch (RuntimeException excecao) {
            LOGGER.warn("Nao foi possivel habilitar TTL em {}: {}. A expiracao continua sendo "
                    + "verificada na leitura.", nome, excecao.getMessage());
        }
    }

    private boolean existe(String nome) {
        try {
            cliente.describeTable(DescribeTableRequest.builder().tableName(nome).build());
            return true;
        } catch (ResourceNotFoundException excecao) {
            return false;
        }
    }
}
