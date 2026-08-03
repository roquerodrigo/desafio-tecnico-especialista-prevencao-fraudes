package br.com.acme.listas.infraestrutura.dynamodb;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * Client do DynamoDB configurado a mao, sem {@code spring-cloud-aws}.
 *
 * <p>O starter do Spring Cloud AWS 3.x tem como alvo o Spring Boot 3.x; depender dele aqui
 * arriscaria conflito de autoconfiguracao por um ganho pequeno — este client cabe em dez linhas.
 *
 * <p>O endpoint e parametrizado: vazio usa a resolucao padrao da AWS; preenchido aponta para o
 * DynamoDB Local. O mesmo codigo roda nos dois ambientes sem alteracao.
 */
@Configuration
public class ConfiguracaoDynamoDb {

    @Bean
    DynamoDbClient dynamoDbClient(
            @Value("${app.dynamodb.endpoint:}") String endpoint,
            @Value("${app.dynamodb.regiao}") String regiao,
            @Value("${app.dynamodb.chave-acesso:}") String chaveAcesso,
            @Value("${app.dynamodb.chave-secreta:}") String chaveSecreta) {

        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(regiao));

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        if (!chaveAcesso.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(chaveAcesso, chaveSecreta)));
        }
        return builder.build();
    }

    @Bean
    DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }
}
