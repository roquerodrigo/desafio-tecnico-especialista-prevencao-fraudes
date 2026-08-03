package br.com.acme.listas.infraestrutura.web;

import br.com.acme.listas.dominio.EntradaListaInvalidaException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorErroGlobal {

    private static final String BASE_TIPO = "https://acme.com.br/erros/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacaoDeContrato(MethodArgumentNotValidException excecao) {
        List<Map<String, String>> erros = excecao.getBindingResult().getFieldErrors().stream()
                .map(erro -> Map.of(
                        "campo", erro.getField(),
                        "mensagem", erro.getDefaultMessage() == null ? "valor invalido" : erro.getDefaultMessage()))
                .toList();
        return problemaDeValidacao(erros);
    }

    @ExceptionHandler(EntradaListaInvalidaException.class)
    public ProblemDetail tratarEntradaInvalida(EntradaListaInvalidaException excecao) {
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    private ProblemDetail problemaDeValidacao(List<Map<String, String>> erros) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail("Um ou mais campos estão inválidos");
        problema.setProperty("erros", erros);
        return problema;
    }
}
