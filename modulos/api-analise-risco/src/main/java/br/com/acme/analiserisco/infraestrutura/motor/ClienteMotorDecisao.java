package br.com.acme.analiserisco.infraestrutura.motor;

import br.com.acme.analiserisco.aplicacao.AnaliseIndisponivelException;
import br.com.acme.analiserisco.aplicacao.porta.CalculoScorePort;
import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.Transacao;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador para o motor de decisao. Implementa a politica de <b>fail-closed</b>.
 *
 * <p>Ao contrario das listas, a falha aqui e propagada: sem score nao existe decisao. Devolver um
 * score arbitrario seria pior que admitir a indisponibilidade, porque a orquestracao o trataria
 * como avaliacao legitima.
 */
@Component
public class ClienteMotorDecisao implements CalculoScorePort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClienteMotorDecisao.class);

    private final RestClient restClient;

    public ClienteMotorDecisao(RestClient.Builder builder,
                               @Value("${app.servicos.motor-decisao.url}") String url) {
        this.restClient = builder.baseUrl(url).build();
    }

    @Override
    public ResultadoCalculo calcular(Transacao transacao, ResultadoListas resultadoListas) {
        try {
            CalculoScoreResponse resposta = restClient.post()
                    .uri("/v1/scores")
                    .body(new CalculoScoreRequest(
                            transacao.tipoTransacao(),
                            transacao.valorTransacao(),
                            resultadoListas.cpfEmListaPermissiva(),
                            resultadoListas.cpfEmListaRestritiva(),
                            resultadoListas.ipEmListaRestritiva(),
                            resultadoListas.dispositivoEmListaRestritiva()))
                    .retrieve()
                    .body(CalculoScoreResponse.class);

            if (resposta == null) {
                throw new AnaliseIndisponivelException(
                        "Motor de decisao devolveu corpo vazio", null);
            }
            return traduzir(resposta);
        } catch (AnaliseIndisponivelException excecao) {
            throw excecao;
        } catch (RuntimeException excecao) {
            LOGGER.error("Calculo de score falhou; a analise nao pode ser concluida. Causa: {}",
                    excecao.getMessage());
            throw new AnaliseIndisponivelException("Motor de decisao indisponivel", excecao);
        }
    }

    private ResultadoCalculo traduzir(CalculoScoreResponse resposta) {
        List<RegraAcionada> regras = resposta.regrasAcionadas() == null
                ? List.of()
                : resposta.regrasAcionadas().stream()
                        .map(regra -> new RegraAcionada(regra.chave(), regra.descricao(),
                                regra.acao(), regra.pontos()))
                        .toList();
        return new ResultadoCalculo(resposta.score(), regras);
    }

    record CalculoScoreRequest(
            String tipoTransacao,
            BigDecimal valorTransacao,
            boolean cpfEmListaPermissiva,
            boolean cpfEmListaRestritiva,
            boolean ipEmListaRestritiva,
            boolean dispositivoEmListaRestritiva) {
    }

    record CalculoScoreResponse(int score, List<RegraAcionadaResponse> regrasAcionadas) {
    }

    record RegraAcionadaResponse(String chave, String descricao, String acao, int pontos) {
    }
}
