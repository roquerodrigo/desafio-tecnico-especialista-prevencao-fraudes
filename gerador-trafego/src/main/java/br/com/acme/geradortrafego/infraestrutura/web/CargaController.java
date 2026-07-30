package br.com.acme.geradortrafego.infraestrutura.web;

import br.com.acme.geradortrafego.aplicacao.ExecutarCargaUseCase;
import br.com.acme.geradortrafego.dominio.ResultadoCarga;
import br.com.acme.geradortrafego.infraestrutura.web.dto.CargaDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/cargas")
@Tag(name = "Carga (administrativo)",
        description = "Geração de tráfego sintético contra a API de análise de risco")
public class CargaController {

    private final ExecutarCargaUseCase executarCarga;

    public CargaController(ExecutarCargaUseCase executarCarga) {
        this.executarCarga = executarCarga;
    }

    @PostMapping
    @Operation(summary = "Inicia uma carga",
            description = "Dispara tráfego sintético com CPFs válidos e distribuição calibrada para "
                    + "exercitar as quatro faixas de valor, os três tipos de transação e os sinais de "
                    + "lista. Responde imediatamente; acompanhe por GET /v1/cargas/atual.")
    public ResponseEntity<ResultadoCarga.Relatorio> iniciar(
            @Valid @RequestBody(required = false) CargaDto.Requisicao requisicao) {

        CargaDto.Requisicao parametros = requisicao == null
                ? new CargaDto.Requisicao(null, null, null)
                : requisicao;

        return ResponseEntity.accepted().body(executarCarga.iniciar(
                parametros.duracaoOuPadrao(),
                parametros.requisicoesPorSegundoOuPadrao(),
                parametros.sementeOuAleatoria()));
    }

    @GetMapping("/atual")
    @Operation(summary = "Relatório da carga",
            description = "Devolve a carga em andamento ou, se nenhuma estiver ativa, a última concluída. "
                    + "O campo metaAtingida compara o p95 medido com a meta de 150 ms (SC-001).")
    public ResponseEntity<ResultadoCarga.Relatorio> consultar() {
        return executarCarga.relatorioAtual()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/atual")
    @Operation(summary = "Interrompe a carga em andamento")
    public ResponseEntity<ResultadoCarga.Relatorio> interromper() {
        return executarCarga.interromper()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
