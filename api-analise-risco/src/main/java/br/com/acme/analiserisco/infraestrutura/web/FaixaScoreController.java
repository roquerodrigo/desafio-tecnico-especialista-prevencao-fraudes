package br.com.acme.analiserisco.infraestrutura.web;

import br.com.acme.analiserisco.aplicacao.GerenciarFaixasUseCase;
import br.com.acme.analiserisco.infraestrutura.web.dto.FaixasScoreDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/faixas-score")
@Tag(name = "Faixas de Score (administrativo)",
        description = "Configuração das faixas de risco e da política de decisão, em runtime")
public class FaixaScoreController {

    private final GerenciarFaixasUseCase gerenciarFaixas;

    public FaixaScoreController(GerenciarFaixasUseCase gerenciarFaixas) {
        this.gerenciarFaixas = gerenciarFaixas;
    }

    @GetMapping
    @Operation(summary = "Consulta as faixas vigentes",
            description = "limiteSuperior nulo significa sem teto.")
    public ResponseEntity<FaixasScoreDto.Resposta> consultar() {
        return ResponseEntity.ok(FaixasScoreDto.Resposta.de(gerenciarFaixas.consultar()));
    }

    @PutMapping
    @Operation(summary = "Substitui integralmente as faixas",
            description = "Substituição total, não parcial: as faixas formam um conjunto com invariantes entre "
                    + "os elementos, e alterar uma isoladamente permitiria estado intermediário inválido. "
                    + "Alterar a decisão de uma faixa muda a política de risco sem deploy.")
    public ResponseEntity<FaixasScoreDto.Resposta> substituir(
            @Valid @RequestBody FaixasScoreDto.Requisicao requisicao) {
        return ResponseEntity.ok(
                FaixasScoreDto.Resposta.de(gerenciarFaixas.substituir(requisicao.paraDominio())));
    }
}
