package br.com.acme.auditoria.infraestrutura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoOpenApi {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Serviço de Auditoria")
                .version("1.0.0")
                .description("Consome as decisões e persiste a trilha de auditoria. Não participa do "
                        + "caminho crítico: sua indisponibilidade não afeta a entrega da decisão ao cliente. "
                        + "As rotas exigem o cabeçalho X-Api-Key."));
    }
}
