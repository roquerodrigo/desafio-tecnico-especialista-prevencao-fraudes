package br.com.acme.analiserisco.infraestrutura.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Timeouts explicitos em toda chamada entre servicos (FR-041).
 *
 * <p>Sem timeout, o cliente herda o default da JVM — que na pratica e esperar indefinidamente. Uma
 * dependencia lenta prenderia threads e transformaria latencia de um vizinho em indisponibilidade
 * propria. Os valores sao curtos de proposito: cabem no orcamento de 150 ms do percentil 95 e fazem
 * a politica de degradacao disparar rapido em vez de acumular espera.
 */
@Configuration
public class ConfiguracaoRestClient {

    @Bean
    ClientHttpRequestFactory clientHttpRequestFactory(
            @Value("${app.http.timeout-conexao-ms}") long conexaoMs,
            @Value("${app.http.timeout-leitura-ms}") long leituraMs) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(conexaoMs))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(leituraMs));
        return factory;
    }

    /**
     * Propaga o identificador de correlacao nas chamadas de saida, para que a mesma analise seja
     * rastreavel nos quatro servicos.
     */
    @Bean
    RestClient.Builder restClientBuilder(ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor((requisicao, corpo, execucao) -> {
                    String idCorrelacao = org.slf4j.MDC.get("idCorrelacao");
                    if (idCorrelacao != null) {
                        requisicao.getHeaders().add("X-Correlation-Id", idCorrelacao);
                    }
                    return execucao.execute(requisicao, corpo);
                });
    }
}
