package br.com.acme.motordecisao.infraestrutura.web;

import br.com.acme.motordecisao.aplicacao.ChaveDuplicadaException;
import br.com.acme.motordecisao.aplicacao.ConjuntoRegrasIndisponivelException;
import br.com.acme.motordecisao.aplicacao.RegraNaoEncontradaException;
import br.com.acme.motordecisao.dominio.regra.EscadaInvalidaException;
import br.com.acme.motordecisao.dominio.regra.NaturezaRegraInvalidaException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros em RFC 9457 (Problem Details). Padroniza a resposta de erro em todo o servico.
 */
@RestControllerAdvice
public class TratadorErroGlobal {

    private static final String BASE_TIPO = "https://acme.com.br/erros/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException excecao) {
        List<Map<String, String>> erros = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> Map.of(
                        "campo", erro.getField(),
                        "mensagem", erro.getDefaultMessage() == null ? "valor invalido" : erro.getDefaultMessage()))
                .toList();

        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail("Um ou mais campos estão inválidos");
        problema.setProperty("erros", erros);
        return problema;
    }

    @ExceptionHandler(RegraNaoEncontradaException.class)
    public ProblemDetail tratarNaoEncontrada(RegraNaoEncontradaException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problema.setType(URI.create(BASE_TIPO + "regra-nao-encontrada"));
        problema.setTitle("Regra não encontrada");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    @ExceptionHandler(ChaveDuplicadaException.class)
    public ProblemDetail tratarChaveDuplicada(ChaveDuplicadaException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problema.setType(URI.create(BASE_TIPO + "chave-duplicada"));
        problema.setTitle("Chave de sobreposição já utilizada");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    @ExceptionHandler({NaturezaRegraInvalidaException.class, EscadaInvalidaException.class})
    public ProblemDetail tratarRegraInvalida(RuntimeException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problema.setType(URI.create(BASE_TIPO + "regra-invalida"));
        problema.setTitle("Regra inválida");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    @ExceptionHandler(ConjuntoRegrasIndisponivelException.class)
    public org.springframework.http.ResponseEntity<ProblemDetail> tratarIndisponivel(
            ConjuntoRegrasIndisponivelException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problema.setType(URI.create(BASE_TIPO + "regras-indisponiveis"));
        problema.setTitle("Conjunto de regras indisponível");
        problema.setDetail("Não foi possível calcular o score. Tente novamente.");

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.add(HttpHeaders.RETRY_AFTER, "5");
        return new org.springframework.http.ResponseEntity<>(problema, cabecalhos, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
