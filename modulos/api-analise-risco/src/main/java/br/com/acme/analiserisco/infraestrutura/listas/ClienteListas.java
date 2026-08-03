package br.com.acme.analiserisco.infraestrutura.listas;

import br.com.acme.analiserisco.aplicacao.porta.ConsultaListasPort;
import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.Transacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador para o servico de listas. Implementa a politica de <b>degradacao</b>.
 *
 * <p>Qualquer falha — indisponibilidade, timeout, resposta malformada — resulta em
 * {@link ResultadoListas#semConsulta()}, e a analise prossegue. A justificativa e de negocio: se as
 * listas caissem e isso derrubasse a analise, 100% das transacoes deixariam de ser aprovadas por
 * um sinal que e apenas um dos fatores de risco.
 *
 * <p>A traducao do DTO do vizinho para o modelo proprio acontece aqui (ACL): o modelo do servico
 * de listas nao atravessa esta fronteira.
 */
@Component
public class ClienteListas implements ConsultaListasPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClienteListas.class);

    private final RestClient restClient;

    public ClienteListas(RestClient.Builder builder,
                         @org.springframework.beans.factory.annotation.Value("${app.servicos.listas.url}") String url) {
        this.restClient = builder.baseUrl(url).build();
    }

    @Override
    public ResultadoListas consultar(Transacao transacao) {
        try {
            ConsultaListasResponse resposta = restClient.post()
                    .uri("/v1/consultas-listas")
                    .body(new ConsultaListasRequest(
                            transacao.cpf(), transacao.ip(), transacao.idDispositivo().toString()))
                    .retrieve()
                    .body(ConsultaListasResponse.class);

            if (resposta == null) {
                LOGGER.warn("Servico de listas devolveu corpo vazio; degradando para 'nao consta'");
                return ResultadoListas.semConsulta();
            }
            return traduzir(resposta);
        } catch (RuntimeException excecao) {
            LOGGER.warn("Consulta de listas falhou; degradando para 'nao consta'. Causa: {}",
                    excecao.getMessage());
            return ResultadoListas.semConsulta();
        }
    }

    private ResultadoListas traduzir(ConsultaListasResponse resposta) {
        return ResultadoListas.de(
                consta(resposta.cpf() == null ? null : resposta.cpf().permissiva()),
                consta(resposta.cpf() == null ? null : resposta.cpf().restritiva()),
                consta(resposta.ip() == null ? null : resposta.ip().restritiva()),
                consta(resposta.dispositivo() == null ? null : resposta.dispositivo().restritiva()));
    }

    private boolean consta(PertinenciaResponse pertinencia) {
        return pertinencia != null;
    }

    record ConsultaListasRequest(String cpf, String ip, String idDispositivo) {
    }

    record ConsultaListasResponse(VariavelResponse cpf, VariavelResponse ip, VariavelResponse dispositivo) {
    }

    record VariavelResponse(String valor, PertinenciaResponse permissiva, PertinenciaResponse restritiva) {
    }

    record PertinenciaResponse(String idLista, String situacao) {
    }
}
