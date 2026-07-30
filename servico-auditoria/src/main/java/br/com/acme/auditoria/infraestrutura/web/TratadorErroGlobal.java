package br.com.acme.auditoria.infraestrutura.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorErroGlobal {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create("https://acme.com.br/erros/requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail(excecao.getMessage());
        return problema;
    }
}
