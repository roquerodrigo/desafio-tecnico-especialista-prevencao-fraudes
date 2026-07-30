package br.com.acme.motordecisao;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de integracao: Postgres real, com as migrations aplicadas pelo Flyway.
 *
 * <p>Usa Postgres de verdade, e nao H2, porque o schema depende de recursos especificos —
 * {@code UNIQUE} com {@code NULL} nao colidindo, que e o que implementa a unicidade da chave de
 * sobreposicao apenas para regras que declaram chave. Em H2 esse comportamento difere.
 */
@Testcontainers
abstract class SuporteIntegracao {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    /**
     * Desliga a autoconfiguracao do Kafka nos testes de contrato: o broker nao e necessario para
     * verificar HTTP e persistencia, e subir um container a mais tornaria a suite mais lenta sem
     * cobrir nada novo. A publicacao de evento tem teste proprio.
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class ConfiguracaoDeTeste {

        @org.springframework.context.annotation.Bean
        DynamicPropertyRegistrar propriedadesDeTeste() {
            return registro -> registro.add("app.api-key", () -> "chave-de-teste");
        }
    }
}
