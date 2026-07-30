package br.com.acme.analiserisco.infraestrutura.web;

import br.com.acme.analiserisco.aplicacao.AnaliseIndisponivelException;
import br.com.acme.analiserisco.dominio.TabelaFaixasInvalidaException;
import br.com.acme.analiserisco.dominio.TransacaoInvalidaException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorErroGlobal {

    private static final String BASE_TIPO = "https://acme.com.br/erros/";
    private static final Logger LOGGER = LoggerFactory.getLogger(TratadorErroGlobal.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacaoDeContrato(MethodArgumentNotValidException excecao) {
        List<Map<String, String>> erros = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> Map.of(
                        "campo", erro.getField(),
                        "mensagem", erro.getDefaultMessage() == null ? "valor invalido" : erro.getDefaultMessage()))
                .toList();
        return problemaDeValidacao(erros);
    }

    /**
     * Erros levantados pelas invariantes do dominio. {@link TransacaoInvalidaException} reune todos
     * os problemas, de modo que o cliente corrige tudo numa unica tentativa.
     */
    @ExceptionHandler(TransacaoInvalidaException.class)
    public ProblemDetail tratarTransacaoInvalida(TransacaoInvalidaException excecao) {
        List<Map<String, String>> erros = new ArrayList<>();
        for (String problema : excecao.problemas()) {
            int separador = problema.indexOf(':');
            if (separador > 0) {
                erros.add(Map.of(
                        "campo", problema.substring(0, separador).trim(),
                        "mensagem", problema.substring(separador + 1).trim()));
            } else {
                erros.add(Map.of("campo", "requisicao", "mensagem", problema));
            }
        }
        return problemaDeValidacao(erros);
    }

    private ProblemDetail problemaDeValidacao(List<Map<String, String>> erros) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail("Um ou mais campos estão inválidos");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(TabelaFaixasInvalidaException.class)
    public ProblemDetail tratarFaixasInvalidas(TabelaFaixasInvalidaException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problema.setType(URI.create(BASE_TIPO + "configuracao-invalida"));
        problema.setTitle("Configuração de faixas inválida");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    /**
     * Fail-closed. O {@code detail} e deliberadamente generico: nao revela qual dependencia falhou,
     * e nao expoe score nem classificacao — nem em cenario de erro (FR-008).
     */
    @ExceptionHandler(AnaliseIndisponivelException.class)
    public ResponseEntity<ProblemDetail> tratarAnaliseIndisponivel(AnaliseIndisponivelException excecao) {
        LOGGER.error("Analise nao concluida por indisponibilidade: {}", excecao.getMessage());

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problema.setType(URI.create(BASE_TIPO + "analise-indisponivel"));
        problema.setTitle("Análise temporariamente indisponível");
        problema.setDetail("Não foi possível concluir a avaliação de risco. Tente novamente.");

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.add(HttpHeaders.RETRY_AFTER, "5");
        return new ResponseEntity<>(problema, cabecalhos, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
