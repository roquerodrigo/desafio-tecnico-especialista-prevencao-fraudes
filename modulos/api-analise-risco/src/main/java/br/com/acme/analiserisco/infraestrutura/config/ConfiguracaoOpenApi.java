package br.com.acme.analiserisco.infraestrutura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoOpenApi {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("API de Análise de Risco")
                .version("1.0.0")
                .description("Orquestra a avaliação de risco de transações e devolve a decisão. "
                        + "A resposta expõe exclusivamente a decisão — score e classificação de risco "
                        + "nunca são retornados. As rotas sob /v1/faixas-score são administrativas e "
                        + "exigem o cabeçalho X-Api-Key."));
    }
}
