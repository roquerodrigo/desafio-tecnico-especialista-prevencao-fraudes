package br.com.acme.auditoria.infraestrutura.observabilidade;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protege apenas as rotas administrativas. A rota de calculo de score permanece aberta por ser
 * chamada intra-cluster — premissa registrada no README.
 */
@Component
@Order(2)
public class FiltroApiKey extends OncePerRequestFilter {

    private static final String CABECALHO = "X-Api-Key";
    private static final String PREFIXO_ADMINISTRATIVO = "/v1/trilhas";

    private final String chaveEsperada;

    public FiltroApiKey(@Value("${app.api-key}") String chaveEsperada) {
        this.chaveEsperada = chaveEsperada;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return !requisicao.getRequestURI().startsWith(PREFIXO_ADMINISTRATIVO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain cadeia) throws ServletException, IOException {
        String informada = requisicao.getHeader(CABECALHO);
        if (chaveEsperada.equals(informada)) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }

        resposta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.getWriter().write("""
                {
                  "type": "https://acme.com.br/erros/nao-autorizado",
                  "title": "Não autorizado",
                  "status": 401,
                  "detail": "Cabeçalho X-Api-Key ausente ou inválido"
                }""");
    }
}
