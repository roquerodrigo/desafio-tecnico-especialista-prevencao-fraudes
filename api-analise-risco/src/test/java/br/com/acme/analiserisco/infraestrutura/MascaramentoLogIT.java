package br.com.acme.analiserisco.infraestrutura;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SC-010 — nenhum dado pessoal completo pode aparecer em registro de log.
 *
 * <p>O {@code MascaradorDadosSensiveisTest} verifica o mascarador isoladamente, mas isso nao prova
 * que ele esta <b>aplicado</b> no caminho real: bastaria um {@code LOGGER.info} novo com o CPF cru
 * para o requisito ser violado sem que nenhum teste falhasse.
 *
 * <p>Aqui a saida de log e capturada durante uma analise completa e inspecionada. E o teste que
 * protege contra regressao futura — inclusive contra codigo escrito por outra pessoa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("teste")
class MascaramentoLogIT {

    private static final String CPF = "52998224725";
    private static final String IP = "203.0.113.42";
    private static final String DISPOSITIVO = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

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
    }

    @AfterAll
    static void encerrar() {
        LISTAS.stop();
        MOTOR.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    private ListAppender<ILoggingEvent> capturaDeLog;
    private Logger loggerRaiz;

    /**
     * Captura no logger raiz, sem alterar niveis — o objetivo e verificar a saida que a aplicacao
     * produz <b>na configuracao real</b>.
     *
     * <p>Forcar DEBUG aqui tornaria o teste vermelho por um motivo legitimo mas fora do controle da
     * aplicacao: em DEBUG, o Spring Web registra o corpo desserializado da requisicao, com CPF e IP
     * em claro. A protecao contra isso e o piso `org.springframework.web: INFO` no
     * `application.yml`, e ela tem teste proprio abaixo.
     */
    @BeforeEach
    void capturarLog() {
        loggerRaiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        capturaDeLog = new ListAppender<>();
        capturaDeLog.start();
        loggerRaiz.addAppender(capturaDeLog);
    }

    @AfterEach
    void liberarLog() {
        loggerRaiz.detachAppender(capturaDeLog);
        capturaDeLog.stop();
    }

    private List<String> mensagensCapturadas() {
        return capturaDeLog.list.stream()
                .map(evento -> evento.getFormattedMessage()
                        + " " + java.util.Arrays.toString(evento.getArgumentArray()))
                .toList();
    }

    private void analisar() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/v1/analises-risco")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "cpf": "%s",
                          "ip": "%s",
                          "idDispositivo": "%s",
                          "tipoTransacao": "PIX",
                          "valorTransacao": 1500.00
                        }""".formatted(CPF, IP, DISPOSITIVO)));
    }

    @Test
    @DisplayName("analise bem-sucedida nao registra CPF, IP nem dispositivo completos em log")
    void analiseComSucessoNaoVazaDadoSensivel() throws Exception {
        LISTAS.stubFor(post(urlPathEqualTo("/v1/consultas-listas"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "cpf": { "valor": "%s" }, "ip": { "valor": "%s" },
                                  "dispositivo": { "valor": "%s" } }""".formatted(CPF, IP, DISPOSITIVO))));
        MOTOR.stubFor(post(urlPathEqualTo("/v1/scores"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"score\":300,\"regrasAcionadas\":[]}")));

        analisar();

        assertThat(mensagensCapturadas())
                .as("nenhum registro de log pode conter o CPF completo")
                .noneMatch(mensagem -> mensagem.contains(CPF))
                .as("nem o IP completo")
                .noneMatch(mensagem -> mensagem.contains(IP))
                .as("nem o identificador de dispositivo completo")
                .noneMatch(mensagem -> mensagem.contains(DISPOSITIVO));
    }

    @Test
    @DisplayName("degradacao das listas nao vaza dado sensivel ao registrar o aviso")
    void degradacaoNaoVazaDadoSensivel() throws Exception {
        LISTAS.stubFor(post(urlPathEqualTo("/v1/consultas-listas"))
                .willReturn(aResponse().withStatus(500)));
        MOTOR.stubFor(post(urlPathEqualTo("/v1/scores"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"score\":300,\"regrasAcionadas\":[]}")));

        analisar();

        assertThat(mensagensCapturadas())
                .anyMatch(mensagem -> mensagem.contains("sem sinal de listas"))
                .noneMatch(mensagem -> mensagem.contains(CPF))
                .noneMatch(mensagem -> mensagem.contains(IP))
                .noneMatch(mensagem -> mensagem.contains(DISPOSITIVO));
    }

    @Test
    @DisplayName("fail-closed nao vaza dado sensivel ao registrar o erro")
    void failClosedNaoVazaDadoSensivel() throws Exception {
        LISTAS.stubFor(post(urlPathEqualTo("/v1/consultas-listas"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "cpf": { "valor": "%s" }, "ip": { "valor": "%s" },
                                  "dispositivo": { "valor": "%s" } }""".formatted(CPF, IP, DISPOSITIVO))));
        MOTOR.stubFor(post(urlPathEqualTo("/v1/scores"))
                .willReturn(aResponse().withStatus(503)));

        analisar();

        assertThat(mensagensCapturadas())
                .as("o log de indisponibilidade nao pode carregar o dado do cliente")
                .noneMatch(mensagem -> mensagem.contains(CPF))
                .noneMatch(mensagem -> mensagem.contains(IP))
                .noneMatch(mensagem -> mensagem.contains(DISPOSITIVO));
    }

    @Test
    @DisplayName("entrada invalida nao ecoa o CPF recebido no log")
    void entradaInvalidaNaoEcoaCpf() throws Exception {
        String cpfInvalido = "11111111111";

        mockMvc.perform(MockMvcRequestBuilders.post("/v1/analises-risco")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "cpf": "%s",
                          "ip": "%s",
                          "idDispositivo": "%s",
                          "tipoTransacao": "PIX",
                          "valorTransacao": 1500.00
                        }""".formatted(cpfInvalido, IP, DISPOSITIVO)));

        assertThat(mensagensCapturadas())
                .noneMatch(mensagem -> mensagem.contains(cpfInvalido))
                .noneMatch(mensagem -> mensagem.contains(IP));
    }

    /**
     * Este teste guarda a descoberta que motivou o piso de log.
     *
     * <p>Em DEBUG, o Spring Web registra o corpo desserializado da requisicao —
     * {@code Read ... [AnaliseRiscoRequest[cpf=52998224725, ip=...]} — contornando todo o
     * mascaramento da aplicacao. Como ligar DEBUG para investigar um incidente e rotina
     * operacional, o nivel fica fixado em INFO no {@code application.yml}.
     *
     * <p>O teste falha se alguem remover esse piso, que e exatamente a regressao a evitar.
     */
    @Test
    @DisplayName("o nivel do Spring Web esta fixado em INFO — em DEBUG ele vazaria o payload")
    void nivelDoSpringWebImpedeVazamentoPorDebug() {
        Logger loggerSpringWeb = (Logger) LoggerFactory.getLogger("org.springframework.web");

        assertThat(loggerSpringWeb.getEffectiveLevel())
                .as("em DEBUG o Spring Web registra o corpo da requisicao com CPF e IP em claro")
                .isEqualTo(Level.INFO);
        assertThat(loggerSpringWeb.isDebugEnabled()).isFalse();
    }
}
