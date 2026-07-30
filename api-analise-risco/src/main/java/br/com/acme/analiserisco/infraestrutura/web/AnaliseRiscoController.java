package br.com.acme.analiserisco.infraestrutura.web;

import br.com.acme.analiserisco.aplicacao.AnalisarRiscoUseCase;
import br.com.acme.analiserisco.infraestrutura.web.dto.AnaliseRiscoRequest;
import br.com.acme.analiserisco.infraestrutura.web.dto.AnaliseRiscoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/analises-risco")
@Tag(name = "Análise de Risco", description = "Avaliação de risco de transações financeiras")
public class AnaliseRiscoController {

    private final AnalisarRiscoUseCase analisarRisco;

    public AnaliseRiscoController(AnalisarRiscoUseCase analisarRisco) {
        this.analisarRisco = analisarRisco;
    }

    @PostMapping
    @Operation(summary = "Avalia o risco de uma transação",
            description = "Consulta listas, obtém o score, classifica o risco e devolve a decisão. "
                    + "A resposta contém exclusivamente a decisão — score e classificação nunca são expostos. "
                    + "Retorna 200 (não 201) porque nenhum recurso observável é criado para o cliente.")
    public ResponseEntity<AnaliseRiscoResponse> analisar(@Valid @RequestBody AnaliseRiscoRequest requisicao) {
        AnalisarRiscoUseCase.ResultadoAnalise resultado = analisarRisco.executar(requisicao.paraDominio());
        return ResponseEntity.ok(new AnaliseRiscoResponse(resultado.decisao()));
    }
}
