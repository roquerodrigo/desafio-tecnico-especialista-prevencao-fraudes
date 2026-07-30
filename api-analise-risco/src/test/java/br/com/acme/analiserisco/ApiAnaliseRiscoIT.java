package br.com.acme.analiserisco;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integracao da orquestracao. Postgres real para as faixas; WireMock dublando listas e motor.
 *
 * <p>WireMock, e nao mock de objeto, porque o que precisa ser exercitado aqui e a fronteira HTTP:
 * serializacao real, timeout real e a traducao na camada de ACL. Um mock de porta pularia
 * justamente o codigo que traduz e degrada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("teste")
class ApiAnaliseRiscoIT {

    private static final String API_KEY = "chave-de-teste";
    private static final String CPF = "52998224725";
    private static final String DISPOSITIVO = "11111111-1111-4111-8111-111111111111";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static final WireMockServer LISTAS = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final WireMockServer MOTOR = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        LISTAS.start();
        MOTOR.start();
        registro.add("app.servicos.listas.url", LISTAS::baseUrl);
        registro.add("app.servicos.motor-decisao.url", MOTOR::baseUrl);
        registro.add("app.api-key", () -> API_KEY);
    }

    @AfterAll
    static void encerrar() {
        LISTAS.stop();
        MOTOR.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void limparDubles() {
        LISTAS.resetAll();
        MOTOR.resetAll();
    }

    private void listasRespondem(String corpo) {
        LISTAS.stubFor(post(urlPathEqualTo("/v1/consultas-listas"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(corpo)));
    }

    private void listasFalham() {
        LISTAS.stubFor(post(urlPathEqualTo("/v1/consultas-listas"))
                .willReturn(aResponse().withStatus(500)));
    }

    private void motorResponde(int score) {
        MOTOR.stubFor(post(urlPathEqualTo("/v1/scores"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "score": %d,
                                  "regrasAcionadas": [
                                    { "chave": "faixa_valor_2", "descricao": "faixa 2", "acao": "SOMAR", "pontos": %d }
                                  ]
                                }""".formatted(score, score))));
    }

    private void motorFalha() {
        MOTOR.stubFor(post(urlPathEqualTo("/v1/scores"))
                .willReturn(aResponse().withStatus(503)));
    }

    private static final String LISTAS_VAZIAS = """
            { "cpf": { "valor": "%s" }, "ip": { "valor": "198.51.100.10" },
              "dispositivo": { "valor": "%s" } }""".formatted(CPF, DISPOSITIVO);

    private MvcResult analisar(String valor) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s",
                                  "ip": "198.51.100.10",
                                  "idDispositivo": "%s",
                                  "tipoTransacao": "PIX",
                                  "valorTransacao": %s
                                }""".formatted(CPF, DISPOSITIVO, valor)))
                .andReturn();
    }

    @Test
    @DisplayName("aprova quando o score cai em faixa de aprovacao")
    void aprovaScoreBaixo() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorResponde(300);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 150.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("APROVADA"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    @DisplayName("nega quando o score cai em faixa de negativa")
    void negaScoreAlto() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorResponde(800);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 25000.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("NEGADA"));
    }

    @Test
    @DisplayName("a resposta contem exclusivamente a decisao — nunca score nem classificacao")
    void respostaSoContemDecisao() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorResponde(300);

        MvcResult resultado = analisar("150.00");
        String corpo = resultado.getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo)
                .isEqualTo("{\"decisao\":\"APROVADA\"}")
                .doesNotContain("score")
                .doesNotContain("classificacao")
                .doesNotContain("BAIXO")
                .doesNotContain("regras");

        org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getHeaderNames())
                .noneSatisfy(nome -> org.assertj.core.api.Assertions.assertThat(nome.toLowerCase())
                        .contains("score"));
    }

    @Test
    @DisplayName("degrada quando o servico de listas falha, e ainda decide")
    void degradaComListasFora() throws Exception {
        listasFalham();
        motorResponde(300);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 150.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisao").value("APROVADA"));
    }

    @Test
    @DisplayName("fail-closed quando o motor falha: 503 com Retry-After, nunca 200 NEGADA")
    void failClosedComMotorFora() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorFalha();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 150.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.type").value("https://acme.com.br/erros/analise-indisponivel"))
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.classificacao").doesNotExist());
    }

    @Test
    @DisplayName("rejeita CPF sequencia repetida e valor zero, apontando os dois campos")
    void rejeitaEntradaInvalida() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "11111111111", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 0
                                }""".formatted(DISPOSITIVO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.length()").value(2));
    }

    @Test
    @DisplayName("rejeita valor com tres casas decimais")
    void rejeitaTresCasasDecimais() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 300.005
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].mensagem").value(
                        org.hamcrest.Matchers.containsString("2 casas decimais")));
    }

    @Test
    @DisplayName("aceita e propaga o identificador de correlacao do cliente")
    void propagaCorrelacaoDoCliente() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorResponde(300);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .header("X-Correlation-Id", "correlacao-do-cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 150.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "correlacao-do-cliente"));
    }

    @Test
    @DisplayName("consulta as faixas semeadas pela migration")
    void consultaFaixas() throws Exception {
        mockMvc.perform(get("/v1/faixas-score").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faixas.length()").value(3))
                .andExpect(jsonPath("$.faixas[0].classificacao").value("BAIXO"))
                .andExpect(jsonPath("$.faixas[0].limiteSuperior").value(399))
                .andExpect(jsonPath("$.faixas[2].classificacao").value("ALTO"))
                .andExpect(jsonPath("$.faixas[2].limiteSuperior").doesNotExist());
    }

    @Test
    @DisplayName("alterar a politica de risco muda a decisao sem reiniciar")
    void alterarPoliticaMudaDecisao() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorResponde(500);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 1500.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(jsonPath("$.decisao").value("APROVADA"));

        mockMvc.perform(put("/v1/faixas-score")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "faixas": [
                                  { "classificacao": "BAIXO", "limiteSuperior": 399, "decisao": "APROVADA" },
                                  { "classificacao": "MEDIO", "limiteSuperior": 699, "decisao": "NEGADA" },
                                  { "classificacao": "ALTO", "limiteSuperior": null, "decisao": "NEGADA" }
                                ]}"""))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/v1/analises-risco")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s", "ip": "198.51.100.10", "idDispositivo": "%s",
                                  "tipoTransacao": "PIX", "valorTransacao": 1500.00
                                }""".formatted(CPF, DISPOSITIVO)))
                .andExpect(jsonPath("$.decisao").value("NEGADA"));

        // restaura para nao afetar os demais testes
        mockMvc.perform(put("/v1/faixas-score")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "faixas": [
                                  { "classificacao": "BAIXO", "limiteSuperior": 399, "decisao": "APROVADA" },
                                  { "classificacao": "MEDIO", "limiteSuperior": 699, "decisao": "APROVADA" },
                                  { "classificacao": "ALTO", "limiteSuperior": null, "decisao": "NEGADA" }
                                ]}"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("rejeita configuracao de faixas sem faixa aberta, preservando a vigente")
    void rejeitaFaixasSemFaixaAberta() throws Exception {
        mockMvc.perform(put("/v1/faixas-score")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "faixas": [
                                  { "classificacao": "BAIXO", "limiteSuperior": 399, "decisao": "APROVADA" },
                                  { "classificacao": "MEDIO", "limiteSuperior": 699, "decisao": "APROVADA" }
                                ]}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://acme.com.br/erros/configuracao-invalida"));

        mockMvc.perform(get("/v1/faixas-score").header("X-Api-Key", API_KEY))
                .andExpect(jsonPath("$.faixas.length()").value(3));
    }

    @Test
    @DisplayName("rota administrativa de faixas exige X-Api-Key")
    void faixasExigemCredencial() throws Exception {
        mockMvc.perform(get("/v1/faixas-score")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/faixas-score")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faixas\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rota de analise permanece aberta")
    void analiseEhAberta() throws Exception {
        listasRespondem(LISTAS_VAZIAS);
        motorResponde(300);

        analisar("150.00");
        org.assertj.core.api.Assertions.assertThat(analisar("150.00").getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("CPF em lista restritiva e traduzido e repassado ao motor")
    void traduzSinalDeListaParaOMotor() throws Exception {
        listasRespondem("""
                {
                  "cpf": { "valor": "%s", "restritiva": { "idLista": "abc", "situacao": "ATIVA" } },
                  "ip": { "valor": "198.51.100.10" },
                  "dispositivo": { "valor": "%s" }
                }""".formatted(CPF, DISPOSITIVO));
        motorResponde(600);

        analisar("1500.00");

        MOTOR.verify(com.github.tomakehurst.wiremock.client.WireMock
                .postRequestedFor(urlPathEqualTo("/v1/scores"))
                .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                        .containing("\"cpfEmListaRestritiva\":true")));
    }
}
