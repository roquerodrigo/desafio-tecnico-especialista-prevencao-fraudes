package br.com.acme.motordecisao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integracao do motor contra Postgres real, exercitando o percurso completo: HTTP → caso de uso →
 * JPA → migrations → cache → avaliacao.
 *
 * <p>Kafka fica desabilitado neste teste: a publicacao de evento e verificada separadamente. O foco
 * aqui e o contrato REST e a persistencia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("teste")
class MotorDecisaoIT extends SuporteIntegracao {

    private static final String API_KEY = "chave-de-teste";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheRegras cacheRegras;

    @BeforeEach
    void garantirCacheCarregado() {
        cacheRegras.recarregar();
    }

    @Test
    @DisplayName("calcula score aplicando o conjunto PIX semeado pela migration")
    void calculaScorePix() throws Exception {
        mockMvc.perform(post("/v1/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoTransacao": "PIX",
                                  "valorTransacao": 1500.00,
                                  "cpfEmListaPermissiva": false,
                                  "cpfEmListaRestritiva": true,
                                  "ipEmListaRestritiva": false,
                                  "dispositivoEmListaRestritiva": false
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(600))
                .andExpect(jsonPath("$.regrasAcionadas.length()").value(2));
    }

    @Test
    @DisplayName("regra OU soma seus pontos uma unica vez, com IP e dispositivo restritivos")
    void regraOuSomaUmaVez() throws Exception {
        mockMvc.perform(post("/v1/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoTransacao": "PIX",
                                  "valorTransacao": 150.00,
                                  "cpfEmListaPermissiva": false,
                                  "cpfEmListaRestritiva": false,
                                  "ipEmListaRestritiva": true,
                                  "dispositivoEmListaRestritiva": true
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(700));
    }

    @Test
    @DisplayName("CARTAO herda do PADRAO as chaves que nao redefine")
    void cartaoHerdaDoPadrao() throws Exception {
        mockMvc.perform(get("/v1/regras")
                        .param("tipoTransacao", "CARTAO")
                        .param("efetivo", "true")
                        .header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoTransacao").value("CARTAO"))
                .andExpect(jsonPath("$.regras[?(@.chave == 'faixa_valor_1')].origem").value("CARTAO"))
                .andExpect(jsonPath("$.regras[?(@.chave == 'faixa_valor_2')].origem").value("PADRAO"));
    }

    @Test
    @DisplayName("ciclo completo de CRUD de regra")
    void cicloCompletoDeCrud() throws Exception {
        String corpo = """
                {
                  "chave": "faixa_valor_teste",
                  "tipoTransacao": "BOLETO",
                  "descricao": "Boleto de teste",
                  "escada": "VALOR_TRANSACAO",
                  "limiteSuperior": 1000.00,
                  "acao": { "tipo": "SOMAR", "pontos": 250 }
                }""";

        String resposta = mockMvc.perform(post("/v1/regras")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acao.pontos").value(250))
                .andReturn().getResponse().getContentAsString();

        String id = resposta.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/v1/regras/" + id).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chave").value("faixa_valor_teste"));

        mockMvc.perform(put("/v1/regras/" + id)
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo.replace("250", "480")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acao.pontos").value(480));

        mockMvc.perform(delete("/v1/regras/" + id).header("X-Api-Key", API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/regras/" + id).header("X-Api-Key", API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rejeita chave duplicada no mesmo tipo com 409")
    void rejeitaChaveDuplicada() throws Exception {
        mockMvc.perform(post("/v1/regras")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chave": "faixa_valor_1",
                                  "tipoTransacao": "PIX",
                                  "descricao": "duplicada",
                                  "escada": "VALOR_TRANSACAO",
                                  "limiteSuperior": 300.00,
                                  "acao": { "tipo": "SOMAR", "pontos": 100 }
                                }"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://acme.com.br/erros/chave-duplicada"));
    }

    @Test
    @DisplayName("rejeita regra hibrida — escada com condicoes — com 422")
    void rejeitaRegraHibrida() throws Exception {
        mockMvc.perform(post("/v1/regras")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chave": "hibrida",
                                  "tipoTransacao": "PIX",
                                  "descricao": "hibrida invalida",
                                  "escada": "VALOR_TRANSACAO",
                                  "limiteSuperior": 300.00,
                                  "condicoes": [
                                    { "campo": "CPF_EM_LISTA_RESTRITIVA", "operador": "IGUAL", "valor": "true" }
                                  ],
                                  "acao": { "tipo": "SOMAR", "pontos": 100 }
                                }"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("rejeita campo obrigatorio ausente com 400 e lista o campo")
    void rejeitaCampoObrigatorioAusente() throws Exception {
        mockMvc.perform(post("/v1/regras")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoTransacao": "PIX",
                                  "escada": "VALOR_TRANSACAO",
                                  "limiteSuperior": 300.00,
                                  "acao": { "tipo": "SOMAR", "pontos": 100 }
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros[0].campo").value("descricao"));
    }

    @Test
    @DisplayName("rota administrativa exige X-Api-Key")
    void rotaAdministrativaExigeChave() throws Exception {
        mockMvc.perform(get("/v1/regras")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/regras").header("X-Api-Key", "errada"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rota de score permanece aberta — chamada intra-cluster")
    void rotaDeScoreEhAberta() throws Exception {
        mockMvc.perform(post("/v1/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoTransacao": "PIX",
                                  "valorTransacao": 100.00,
                                  "cpfEmListaPermissiva": false,
                                  "cpfEmListaRestritiva": false,
                                  "ipEmListaRestritiva": false,
                                  "dispositivoEmListaRestritiva": false
                                }"""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("efetivo=true sem tipo de transacao e requisicao invalida")
    void efetivoSemTipoEhInvalido() throws Exception {
        mockMvc.perform(get("/v1/regras").param("efetivo", "true").header("X-Api-Key", API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lista regras cadastradas sem composicao")
    void listaCadastradas() throws Exception {
        mockMvc.perform(get("/v1/regras").param("tipoTransacao", "PADRAO").header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.efetivo").value(false))
                .andExpect(jsonPath("$.regras.length()").value(7));
    }

    @Test
    @DisplayName("tipo sem regras proprias recebe apenas o conjunto PADRAO")
    void tipoDesconhecidoUsaPadrao() throws Exception {
        mockMvc.perform(post("/v1/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tipoTransacao": "TIPO_INEXISTENTE",
                                  "valorTransacao": 150.00,
                                  "cpfEmListaPermissiva": false,
                                  "cpfEmListaRestritiva": false,
                                  "ipEmListaRestritiva": false,
                                  "dispositivoEmListaRestritiva": false
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(200));
    }

    @Test
    @DisplayName("cache carregado a partir das migrations")
    void cacheCarregado() {
        assertThat(cacheRegras.carregado()).isTrue();
        assertThat(cacheRegras.conjuntoEfetivoDe("PIX")).isPresent();
        assertThat(cacheRegras.conjuntoEfetivoDe("QUALQUER_OUTRO")).isPresent();
    }
}
