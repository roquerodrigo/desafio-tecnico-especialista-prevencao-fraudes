package br.com.acme.motordecisao.infraestrutura.web;

import br.com.acme.motordecisao.aplicacao.CalcularScoreUseCase;
import br.com.acme.motordecisao.dominio.avaliacao.ContextoAvaliacao;
import br.com.acme.motordecisao.infraestrutura.web.dto.CalculoScoreRequest;
import br.com.acme.motordecisao.infraestrutura.web.dto.CalculoScoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scores")
@Tag(name = "Score", description = "Cálculo do score de risco a partir das regras vigentes")
public class ScoreController {

    private final CalcularScoreUseCase calcularScore;

    public ScoreController(CalcularScoreUseCase calcularScore) {
        this.calcularScore = calcularScore;
    }

    @PostMapping
    @Operation(summary = "Calcula o score de risco",
            description = "Aplica o conjunto efetivo de regras do tipo de transação e devolve o score. "
                    + "Não classifica risco nem decide — isso é responsabilidade de quem orquestra.")
    public ResponseEntity<CalculoScoreResponse> calcular(@Valid @RequestBody CalculoScoreRequest requisicao) {
        ContextoAvaliacao contexto = new ContextoAvaliacao(
                requisicao.tipoTransacao(),
                requisicao.valorTransacao(),
                requisicao.cpfEmListaPermissiva(),
                requisicao.cpfEmListaRestritiva(),
                requisicao.ipEmListaRestritiva(),
                requisicao.dispositivoEmListaRestritiva());

        return ResponseEntity.ok(CalculoScoreResponse.de(calcularScore.executar(contexto)));
    }
}
