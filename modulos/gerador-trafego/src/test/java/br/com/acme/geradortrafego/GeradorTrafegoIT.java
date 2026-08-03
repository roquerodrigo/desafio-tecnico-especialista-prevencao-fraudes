package br.com.acme.geradortrafego;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integracao do gerador com a API de analise dublada por WireMock.
 *
 * <p>WireMock em vez de mock de porta porque o que interessa aqui e a fronteira HTTP real: a
 * serializacao do corpo, a traducao de erro em motivo agrupavel e o comportamento sob resposta
 * de falha. Um mock de objeto pularia o adaptador, que e justamente o codigo sob teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GeradorTrafegoIT {

    private static final String API_KEY = "chave-de-teste";

    static final WireMockServer ANALISE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        ANALISE.start();
        registro.add("app.servicos.analise-risco.url", ANALISE::baseUrl);
        registro.add("app.api-key", () -> API_KEY);
    }

    @AfterAll
    static void encerrar() {
        ANALISE.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void limpar() throws Exception {
        ANALISE.resetAll();
        mockMvc.perform(delete("/v1/cargas/atual").header("X-Api-Key", API_KEY));
    }

    private void analiseResponde(String decisao) {
        ANALISE.stubFor(post(urlPathEqualTo("/v1/analises-risco"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"decisao\":\"" + decisao + "\"}")));
    }

    private void analiseFalha(int status) {
        ANALISE.stubFor(post(urlPathEqualTo("/v1/analises-risco"))
                .willReturn(aResponse().withStatus(status)));
    }

    private void aguardarConclusao() throws Exception {
        for (int tentativa = 0; tentativa < 60; tentativa++) {
            String corpo = mockMvc.perform(get("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                    .andReturn().getResponse().getContentAsString();
            if (!corpo.contains("EM_EXECUCAO")) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("a carga nao concluiu no tempo esperado");
    }

    private void iniciarCarga(String corpo) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/cargas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("executa uma carga e reporta latencia, decisoes e meta de p95")
    void executaCargaEReporta() throws Exception {
        analiseResponde("APROVADA");

        iniciarCarga("{\"duracaoSegundos\":1,\"requisicoesPorSegundo\":20,\"semente\":42}");
        aguardarConclusao();

        mockMvc.perform(get("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDA"))
                .andExpect(jsonPath("$.requisicoes.total").value(20))
                .andExpect(jsonPath("$.requisicoes.sucesso").value(20))
                .andExpect(jsonPath("$.requisicoes.erro").value(0))
                .andExpect(jsonPath("$.decisoes.APROVADA").value(20))
                .andExpect(jsonPath("$.latenciaMs.p95").exists())
                .andExpect(jsonPath("$.metaP95Ms").value(150))
                .andExpect(jsonPath("$.metaAtingida").value(true));
    }

    @Test
    @DisplayName("envia CPF valido de 11 digitos e valor com no maximo duas casas decimais")
    void enviaCargaUtilValida() throws Exception {
        analiseResponde("APROVADA");

        iniciarCarga("{\"duracaoSegundos\":1,\"requisicoesPorSegundo\":10,\"semente\":42}");
        aguardarConclusao();

        ANALISE.verify(postRequestedFor(urlPathEqualTo("/v1/analises-risco"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .matchingJsonPath("$.cpf", com.github.tomakehurst.wiremock.client.WireMock
                                .matching("\\d{11}"))));
    }

    @Test
    @DisplayName("resposta de erro da API vira motivo agrupavel, sem interromper a carga")
    void erroViraMotivoAgrupavel() throws Exception {
        analiseFalha(503);

        iniciarCarga("{\"duracaoSegundos\":1,\"requisicoesPorSegundo\":10,\"semente\":42}");
        aguardarConclusao();

        mockMvc.perform(get("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("CONCLUIDA"))
                .andExpect(jsonPath("$.requisicoes.erro").value(10))
                .andExpect(jsonPath("$.errosPorMotivo.HTTP_503").value(10))
                .andExpect(jsonPath("$.metaAtingida")
                        .value(false));
    }

    @Test
    @DisplayName("usa os valores padrao quando o corpo e omitido")
    void corpoOmitidoUsaPadroes() throws Exception {
        analiseResponde("APROVADA");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/cargas").header("X-Api-Key", API_KEY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duracaoSegundosPlanejada").value(30))
                .andExpect(jsonPath("$.requisicoesPorSegundoPlanejada").value(50));

        mockMvc.perform(delete("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("recusa segunda carga simultanea com 409")
    void recusaCargaSimultanea() throws Exception {
        analiseResponde("APROVADA");

        iniciarCarga("{\"duracaoSegundos\":30,\"requisicoesPorSegundo\":5}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/cargas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoSegundos\":5,\"requisicoesPorSegundo\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://acme.com.br/erros/carga-em-andamento"));

        mockMvc.perform(delete("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("INTERROMPIDA"));
    }

    @Test
    @DisplayName("interromper sem carga em andamento devolve 204")
    void interromperSemCarga() throws Exception {
        mockMvc.perform(delete("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("rejeita parametros fora dos limites com 400")
    void rejeitaParametrosInvalidos() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/cargas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoSegundos\":0,\"requisicoesPorSegundo\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("duracaoSegundos"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/cargas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duracaoSegundos\":10,\"requisicoesPorSegundo\":5000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("as rotas de carga exigem X-Api-Key")
    void rotasExigemCredencial() throws Exception {
        mockMvc.perform(get("/v1/cargas/atual")).andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/cargas")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/cargas/atual").header("X-Api-Key", "errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("propaga o identificador de correlacao")
    void propagaCorrelacao() throws Exception {
        mockMvc.perform(get("/v1/cargas/atual")
                        .header("X-Api-Key", API_KEY)
                        .header("X-Correlation-Id", "correlacao-de-teste"))
                .andExpect(header().string("X-Correlation-Id", "correlacao-de-teste"));
    }

    // O caso "sem nenhuma carga executada devolve 204" vive em ExecutarCargaUseCaseTest: aqui o
    // ApplicationContext e compartilhado entre os testes da classe, e o relatorio da ultima carga
    // persiste de proposito — um teste que exigisse estado limpo dependeria da ordem de execucao.

    @Test
    @DisplayName("resposta sem corpo da API e contabilizada como erro")
    void respostaVaziaViraErro() throws Exception {
        ANALISE.stubFor(post(urlPathEqualTo("/v1/analises-risco"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        iniciarCarga("{\"duracaoSegundos\":1,\"requisicoesPorSegundo\":5,\"semente\":42}");
        aguardarConclusao();

        mockMvc.perform(get("/v1/cargas/atual").header("X-Api-Key", API_KEY))
                .andExpect(jsonPath("$.errosPorMotivo.RESPOSTA_VAZIA").value(5));
    }
}
