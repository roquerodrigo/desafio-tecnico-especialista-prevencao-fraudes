package br.com.acme.motordecisao.infraestrutura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoOpenApi {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Motor de Decisão")
                .version("1.0.0")
                .description("Calcula o score de risco aplicando regras dinâmicas e mantém o CRUD dessas regras. "
                        + "As rotas sob /v1/regras são administrativas e exigem o cabeçalho X-Api-Key."));
    }
}
