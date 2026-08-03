package br.com.acme.geradortrafego.infraestrutura.cliente;

import br.com.acme.geradortrafego.aplicacao.porta.ClienteAnaliseRisco;
import br.com.acme.geradortrafego.dominio.PerfilTrafego;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador HTTP para a API de analise de risco.
 *
 * <p>Toda falha vira um {@code RespostaAnalise} de erro com motivo agrupavel, nunca excecao
 * propagada: no gerador, erro e dado da medicao. O motivo e curto de proposito (por exemplo
 * {@code HTTP_503}), para que o relatorio agrupe em vez de listar milhares de mensagens distintas.
 *
 * <p>Note que {@code 503} do fail-closed aparece aqui como erro — e correto: do ponto de vista do
 * cliente, a analise nao aconteceu. E exatamente o que se quer ver contabilizado ao derrubar o
 * motor durante uma carga.
 */
@Component
public class ClienteAnaliseRiscoHttp implements ClienteAnaliseRisco {

    private final RestClient restClient;

    public ClienteAnaliseRiscoHttp(RestClient.Builder builder,
                                   @Value("${app.servicos.analise-risco.url}") String url) {
        this.restClient = builder.baseUrl(url).build();
    }

    @Override
    public RespostaAnalise analisar(PerfilTrafego.TransacaoSintetica transacao) {
        try {
            AnaliseResponse resposta = restClient.post()
                    .uri("/v1/analises-risco")
                    .body(new AnaliseRequest(
                            transacao.cpf(),
                            transacao.ip(),
                            transacao.idDispositivo(),
                            transacao.tipoTransacao(),
                            transacao.valorTransacao()))
                    .retrieve()
                    .body(AnaliseResponse.class);

            if (resposta == null || resposta.decisao() == null) {
                return RespostaAnalise.erro("RESPOSTA_VAZIA");
            }
            return RespostaAnalise.sucesso(resposta.decisao());
        } catch (RestClientResponseException excecao) {
            return RespostaAnalise.erro("HTTP_" + excecao.getStatusCode().value());
        } catch (RuntimeException excecao) {
            return RespostaAnalise.erro(excecao.getClass().getSimpleName());
        }
    }

    record AnaliseRequest(
            String cpf,
            String ip,
            String idDispositivo,
            String tipoTransacao,
            BigDecimal valorTransacao) {
    }

    record AnaliseResponse(String decisao) {
    }
}
