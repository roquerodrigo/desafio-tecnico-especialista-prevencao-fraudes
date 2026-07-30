package br.com.acme.geradortrafego.infraestrutura.web;

import br.com.acme.geradortrafego.aplicacao.ExecutarCargaUseCase;
import java.net.URI;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail tratarArgumentoInvalido(IllegalArgumentException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problema.setType(URI.create(BASE_TIPO + "requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        problema.setDetail(excecao.getMessage());
        return problema;
    }

    /**
     * Uma carga por vez: execuções simultâneas misturariam amostras e o percentil resultante não
     * descreveria nenhuma das duas.
     */
    @ExceptionHandler(ExecutarCargaUseCase.CargaEmAndamentoException.class)
    public ProblemDetail tratarCargaEmAndamento(ExecutarCargaUseCase.CargaEmAndamentoException excecao) {
        ProblemDetail problema = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problema.setType(URI.create(BASE_TIPO + "carga-em-andamento"));
        problema.setTitle("Carga já em andamento");
        problema.setDetail(excecao.getMessage());
        return problema;
    }
}
