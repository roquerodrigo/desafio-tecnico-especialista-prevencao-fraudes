package br.com.acme.auditoria.infraestrutura.web;

import br.com.acme.auditoria.aplicacao.RegistrarTrilhaUseCase;
import br.com.acme.auditoria.infraestrutura.persistencia.TrilhaDecisaoEntity;
import br.com.acme.auditoria.infraestrutura.web.dto.TrilhaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/trilhas")
@Tag(name = "Trilha de Decisões (administrativo)",
        description = "Consulta da trilha de auditoria das análises concluídas")
public class TrilhaController {

    private static final int LIMITE_PADRAO = 50;

    private final RegistrarTrilhaUseCase registrarTrilha;

    public TrilhaController(RegistrarTrilhaUseCase registrarTrilha) {
        this.registrarTrilha = registrarTrilha;
    }

    @GetMapping
    @Operation(summary = "Consulta a trilha por CPF ou por correlação",
            description = "Devolve score, classificação, decisão, regras acionadas e resultado das listas — "
                    + "informação que nunca é exposta ao cliente da análise.")
    public ResponseEntity<List<TrilhaResponse>> consultar(
            @RequestParam(required = false) String cpf,
            @RequestParam(required = false) String idCorrelacao,
            @RequestParam(required = false, defaultValue = "" + LIMITE_PADRAO) int limite) {

        if (idCorrelacao != null && !idCorrelacao.isBlank()) {
            return ResponseEntity.ok(registrarTrilha.consultarPorCorrelacao(idCorrelacao)
                    .map(TrilhaResponse::de)
                    .map(List::of)
                    .orElseGet(List::of));
        }

        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("Informe cpf ou idCorrelacao");
        }

        List<TrilhaDecisaoEntity> encontradas = registrarTrilha.consultarPorCpf(cpf, limite);
        return ResponseEntity.ok(encontradas.stream().map(TrilhaResponse::de).toList());
    }
}
