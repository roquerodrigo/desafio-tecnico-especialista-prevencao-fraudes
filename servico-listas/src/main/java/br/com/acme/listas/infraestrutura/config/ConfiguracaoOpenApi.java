package br.com.acme.listas.infraestrutura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoOpenApi {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Serviço de Listas")
                .version("1.0.0")
                .description("Verifica se CPF, IP e dispositivo constam em listas permissivas ou restritivas. "
                        + "Um CPF pode constar em ambas; IP e dispositivo apenas em restritivas. "
                        + "A rota PUT /v1/listas é administrativa e exige o cabeçalho X-Api-Key."));
    }
}
