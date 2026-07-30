package br.com.acme.geradortrafego.infraestrutura.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoOpenApi {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Gerador de Tráfego")
                .version("1.0.0")
                .description("Serviço auxiliar que gera tráfego sintético contra a API de análise de risco "
                        + "e mede latência p50/p95/p99. Fica ocioso até receber POST /v1/cargas. "
                        + "As rotas exigem o cabeçalho X-Api-Key."));
    }
}
