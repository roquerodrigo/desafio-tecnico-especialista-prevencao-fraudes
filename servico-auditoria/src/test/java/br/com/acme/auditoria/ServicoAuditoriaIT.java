package br.com.acme.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.acme.auditoria.aplicacao.RegistrarTrilhaUseCase;
import br.com.acme.auditoria.infraestrutura.mensageria.ConsumidorEventoDecisao;
import br.com.acme.auditoria.infraestrutura.mensageria.EventoDecisaoRegistrada;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integracao da auditoria contra Postgres real, incluindo a serializacao dos campos {@code JSONB}
 * de regras acionadas e resultado das listas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("teste")
class ServicoAuditoriaIT {

    private static final String API_KEY = "chave-de-teste";
    private static final String CPF = "52998224725";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConsumidorEventoDecisao consumidor;

    @Autowired
    private RegistrarTrilhaUseCase registrarTrilha;

    private static EventoDecisaoRegistrada evento(String idCorrelacao, String cpf, boolean degradado) {
        return new EventoDecisaoRegistrada(
                UUID.randomUUID(),
                idCorrelacao,
                Instant.parse("2026-07-29T12:00:00Z"),
                cpf,
                "203.0.113.42",
                "11111111-1111-4111-8111-111111111111",
                "PIX",
                new BigDecimal("1500.00"),
                600,
                "MEDIO",
                "APROVADA",
                degradado,
                List.of(new EventoDecisaoRegistrada.RegraAcionada(
                        "faixa_valor_2", "PIX de R$300,01 a R$5.000,00", "SOMAR", 400)),
                new EventoDecisaoRegistrada.ResultadoListas(false, true, false, false));
    }

    @Test
    @DisplayName("persiste a trilha com score, classificacao, decisao e regras acionadas")
    void persisteTrilhaCompleta() throws Exception {
        String correlacao = "corr-completa-" + UUID.randomUUID();

        consumidor.aoReceber(evento(correlacao, CPF, false));

        mockMvc.perform(get("/v1/trilhas")
                        .param("idCorrelacao", correlacao)
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(600))
                .andExpect(jsonPath("$[0].classificacao").value("MEDIO"))
                .andExpect(jsonPath("$[0].decisao").value("APROVADA"))
                .andExpect(jsonPath("$[0].regrasAcionadas[0].chave").value("faixa_valor_2"))
                .andExpect(jsonPath("$[0].resultadoListas.cpfEmListaRestritiva").value(true));
    }

    @Test
    @DisplayName("guarda o CPF em claro — a trilha e base de investigacao")
    void cpfEmClaro() throws Exception {
        String correlacao = "corr-cpf-" + UUID.randomUUID();

        consumidor.aoReceber(evento(correlacao, CPF, false));

        mockMvc.perform(get("/v1/trilhas").param("cpf", CPF).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cpf").value(CPF));
    }

    @Test
    @DisplayName("reentrega do mesmo evento nao duplica registro")
    void reentregaNaoDuplica() throws Exception {
        String correlacao = "corr-idempotente-" + UUID.randomUUID();

        consumidor.aoReceber(evento(correlacao, "11144477735", false));
        consumidor.aoReceber(evento(correlacao, "11144477735", false));
        consumidor.aoReceber(evento(correlacao, "11144477735", false));

        mockMvc.perform(get("/v1/trilhas")
                        .param("idCorrelacao", correlacao)
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("registra que a analise ocorreu com listas degradadas")
    void registraDegradacao() throws Exception {
        String correlacao = "corr-degradada-" + UUID.randomUUID();

        consumidor.aoReceber(evento(correlacao, CPF, true));

        mockMvc.perform(get("/v1/trilhas")
                        .param("idCorrelacao", correlacao)
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].consultaListasDegradada").value(true));
    }

    @Test
    @DisplayName("consulta por correlacao inexistente devolve lista vazia")
    void correlacaoInexistente() throws Exception {
        mockMvc.perform(get("/v1/trilhas")
                        .param("idCorrelacao", "nao-existe")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("consulta sem cpf nem correlacao e requisicao invalida")
    void consultaSemFiltro() throws Exception {
        mockMvc.perform(get("/v1/trilhas").header("X-Api-Key", API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("consulta exige credencial administrativa")
    void exigeCredencial() throws Exception {
        mockMvc.perform(get("/v1/trilhas").param("cpf", CPF))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("evento sem regras nem resultado de listas e tolerado")
    void eventoMinimoEhTolerado() {
        String correlacao = "corr-minima-" + UUID.randomUUID();
        EventoDecisaoRegistrada minimo = new EventoDecisaoRegistrada(
                UUID.randomUUID(), correlacao, Instant.parse("2026-07-29T12:00:00Z"),
                CPF, "203.0.113.42", "11111111-1111-4111-8111-111111111111",
                "PIX", new BigDecimal("100.00"), 1, "BAIXO", "APROVADA", false, null, null);

        consumidor.aoReceber(minimo);

        assertThat(registrarTrilha.consultarPorCorrelacao(correlacao)).isPresent();
    }

    @Test
    @DisplayName("consulta por CPF respeita o limite informado")
    void consultaComLimite() throws Exception {
        String cpfDedicado = "12345678909";
        for (int indice = 0; indice < 3; indice++) {
            consumidor.aoReceber(evento("corr-limite-" + indice + "-" + UUID.randomUUID(), cpfDedicado, false));
        }

        mockMvc.perform(get("/v1/trilhas")
                        .param("cpf", cpfDedicado)
                        .param("limite", "2")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
