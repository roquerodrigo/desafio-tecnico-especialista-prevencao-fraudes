package br.com.acme.listas;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integracao contra DynamoDB Local real.
 *
 * <p>Nao existe modulo oficial do Testcontainers para DynamoDB Local, entao usamos
 * {@link GenericContainer} com a imagem oficial da AWS. As tabelas sao criadas pelo mesmo
 * {@code BootstrapTabelas} que roda no Docker Compose — um unico caminho de criacao de schema.
 *
 * <p>Ordem fixa: a consulta de ausencia precisa rodar antes da carga da mesma variavel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ServicoListasIT {

    private static final String API_KEY = "chave-de-teste";
    private static final String CPF_AMBAS = "52998224725";
    private static final String CPF_SO_RESTRITIVA = "11144477735";
    private static final String IP_RESTRITIVO = "203.0.113.42";
    private static final String IP_EXPIRADO = "203.0.113.99";
    private static final String DISPOSITIVO = "99999999-9999-4999-8999-999999999999";

    @Container
    static final GenericContainer<?> DYNAMODB = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest"))
            .withExposedPorts(8000);

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("app.dynamodb.endpoint",
                () -> "http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000));
        registro.add("app.dynamodb.regiao", () -> "us-east-1");
        registro.add("app.dynamodb.chave-acesso", () -> "teste");
        registro.add("app.dynamodb.chave-secreta", () -> "teste");
        registro.add("app.dynamodb.criar-tabelas", () -> "true");
        registro.add("app.api-key", () -> API_KEY);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    @DisplayName("variavel nao cadastrada nao consta em lista alguma")
    void variavelAusente() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF_AMBAS + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf.permissiva").doesNotExist())
                .andExpect(jsonPath("$.cpf.restritiva").doesNotExist());
    }

    @Test
    @Order(2)
    @DisplayName("carrega entradas nas listas")
    void carregaEntradas() throws Exception {
        mockMvc.perform(put("/v1/listas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entradas": [
                                    {
                                      "tipo": "CPF",
                                      "valor": "%s",
                                      "permissiva": { "idLista": "9c1f0000-0000-4000-8000-000000000001" },
                                      "restritiva": { "idLista": "4a7e0000-0000-4000-8000-000000000001" }
                                    },
                                    {
                                      "tipo": "CPF",
                                      "valor": "%s",
                                      "restritiva": { "idLista": "4a7e0000-0000-4000-8000-000000000001" }
                                    },
                                    {
                                      "tipo": "IP",
                                      "valor": "%s",
                                      "restritiva": { "idLista": "77b20000-0000-4000-8000-000000000001" },
                                      "expiraEm": "2030-01-01T00:00:00Z"
                                    },
                                    {
                                      "tipo": "IP",
                                      "valor": "%s",
                                      "restritiva": { "idLista": "77b20000-0000-4000-8000-000000000001" },
                                      "expiraEm": "2020-01-01T00:00:00Z"
                                    },
                                    {
                                      "tipo": "DISPOSITIVO",
                                      "valor": "%s",
                                      "restritiva": { "idLista": "88c30000-0000-4000-8000-000000000001" }
                                    }
                                  ]
                                }""".formatted(CPF_AMBAS, CPF_SO_RESTRITIVA, IP_RESTRITIVO,
                                IP_EXPIRADO, DISPOSITIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processadas").value(5));
    }

    @Test
    @Order(3)
    @DisplayName("CPF em ambas as listas e reportado nas duas")
    void cpfEmAmbasAsListas() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF_AMBAS + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf.permissiva.situacao").value("ATIVA"))
                .andExpect(jsonPath("$.cpf.restritiva.situacao").value("ATIVA"));
    }

    @Test
    @Order(4)
    @DisplayName("CPF apenas em restritiva nao reporta permissiva")
    void cpfSoEmRestritiva() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF_SO_RESTRITIVA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf.permissiva").doesNotExist())
                .andExpect(jsonPath("$.cpf.restritiva.situacao").value("ATIVA"));
    }

    @Test
    @Order(5)
    @DisplayName("as tres variaveis retornam em uma unica consulta")
    void tresVariaveisEmUmaConsulta() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cpf": "%s",
                                  "ip": "%s",
                                  "idDispositivo": "%s"
                                }""".formatted(CPF_AMBAS, IP_RESTRITIVO, DISPOSITIVO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf.restritiva.situacao").value("ATIVA"))
                .andExpect(jsonPath("$.ip.restritiva.situacao").value("ATIVA"))
                .andExpect(jsonPath("$.dispositivo.restritiva.situacao").value("ATIVA"));
    }

    @Test
    @Order(6)
    @DisplayName("IP com entrada expirada e tratado como ausente")
    void ipExpiradoEhAusente() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ip\":\"" + IP_EXPIRADO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip.restritiva").doesNotExist());
    }

    @Test
    @Order(7)
    @DisplayName("IP e dispositivo nunca expoem lista permissiva")
    void ipEDispositivoNaoTemPermissiva() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ip\":\"" + IP_RESTRITIVO + "\",\"idDispositivo\":\"" + DISPOSITIVO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ip.permissiva").doesNotExist())
                .andExpect(jsonPath("$.dispositivo.permissiva").doesNotExist());
    }

    @Test
    @Order(8)
    @DisplayName("carga sem credencial e rejeitada")
    void cargaSemCredencial() throws Exception {
        mockMvc.perform(put("/v1/listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entradas\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(9)
    @DisplayName("rejeita permissiva em IP com 400")
    void rejeitaPermissivaEmIp() throws Exception {
        mockMvc.perform(put("/v1/listas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entradas": [
                                    {
                                      "tipo": "IP",
                                      "valor": "203.0.113.1",
                                      "permissiva": { "idLista": "9c1f0000-0000-4000-8000-000000000001" }
                                    }
                                  ]
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("permissiva"));
    }

    @Test
    @Order(10)
    @DisplayName("rejeita lote vazio")
    void rejeitaLoteVazio() throws Exception {
        mockMvc.perform(put("/v1/listas")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entradas\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(11)
    @DisplayName("consulta sem nenhuma variavel e requisicao invalida")
    void consultaSemVariavel() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    @DisplayName("rejeita CPF fora do formato de 11 digitos")
    void rejeitaCpfMalformado() throws Exception {
        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"529.982.247-25\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    @DisplayName("sobrescrever a mesma variavel e idempotente")
    void cargaIdempotente() throws Exception {
        String corpo = """
                {
                  "entradas": [
                    {
                      "tipo": "CPF",
                      "valor": "%s",
                      "restritiva": { "idLista": "4a7e0000-0000-4000-8000-000000000002", "situacao": "INATIVA" }
                    }
                  ]
                }""".formatted(CPF_SO_RESTRITIVA);

        mockMvc.perform(put("/v1/listas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/listas").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/consultas-listas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF_SO_RESTRITIVA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf.restritiva").doesNotExist());
    }
}
