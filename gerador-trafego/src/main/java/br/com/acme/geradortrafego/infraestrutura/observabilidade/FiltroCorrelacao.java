package br.com.acme.geradortrafego.infraestrutura.observabilidade;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Propaga o identificador de correlacao. Aceita o do cliente ou gera um, e o publica no MDC para
 * que apareca em todo registro de log da requisicao.
 */
@Component
@Order(1)
public class FiltroCorrelacao extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String CHAVE_MDC = "idCorrelacao";

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain cadeia) throws ServletException, IOException {
        String idCorrelacao = requisicao.getHeader(CABECALHO);
        if (idCorrelacao == null || idCorrelacao.isBlank()) {
            idCorrelacao = UUID.randomUUID().toString();
        }

        MDC.put(CHAVE_MDC, idCorrelacao);
        resposta.setHeader(CABECALHO, idCorrelacao);
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
