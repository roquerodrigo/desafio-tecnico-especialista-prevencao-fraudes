package br.com.acme.motordecisao.infraestrutura.web;

import br.com.acme.motordecisao.aplicacao.GerenciarRegrasUseCase;
import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.infraestrutura.web.dto.RegraRequest;
import br.com.acme.motordecisao.infraestrutura.web.dto.RegraResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/regras")
@Tag(name = "Regras (administrativo)", description = "Gestão das regras de decisão em runtime")
public class RegraController {

    private final GerenciarRegrasUseCase gerenciarRegras;

    public RegraController(GerenciarRegrasUseCase gerenciarRegras) {
        this.gerenciarRegras = gerenciarRegras;
    }

    @GetMapping
    @Operation(summary = "Lista regras",
            description = "Com efetivo=true, devolve o conjunto após a composição com o conjunto PADRÃO, "
                    + "indicando em 'origem' de qual conjunto cada regra veio.")
    public ResponseEntity<Map<String, Object>> listar(
            @RequestParam(required = false) String tipoTransacao,
            @RequestParam(required = false, defaultValue = "false") boolean efetivo) {

        if (efetivo) {
            if (tipoTransacao == null || tipoTransacao.isBlank()) {
                throw new IllegalArgumentException("tipoTransacao e obrigatorio quando efetivo=true");
            }
            ConjuntoEfetivo conjunto = gerenciarRegras.conjuntoEfetivo(tipoTransacao)
                    .orElseThrow(() -> new br.com.acme.motordecisao.aplicacao.ConjuntoRegrasIndisponivelException(
                            "Nenhum conjunto de regras disponivel para o tipo " + tipoTransacao));

            List<RegraResponse> regras = conjunto.todasAsRegras().stream()
                    .map(regra -> RegraResponse.de(regra, conjunto.origemDaChave(regra.chave())))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "tipoTransacao", conjunto.tipoTransacao(),
                    "efetivo", true,
                    "regras", regras));
        }

        List<RegraResponse> regras = gerenciarRegras.listarCadastradas(tipoTransacao).stream()
                .map(RegraResponse::de)
                .toList();

        return ResponseEntity.ok(Map.of("efetivo", false, "regras", regras));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma regra por identificador")
    public ResponseEntity<RegraResponse> buscar(@PathVariable UUID id) {
        return ResponseEntity.ok(RegraResponse.de(gerenciarRegras.buscar(id)));
    }

    @PostMapping
    @Operation(summary = "Cria uma regra",
            description = "A alteração passa a valer nas próximas análises, sem redeploy e sem restart.")
    public ResponseEntity<RegraResponse> criar(@Valid @RequestBody RegraRequest requisicao) {
        Regra criada = gerenciarRegras.criar(requisicao.paraDominio(UUID.randomUUID()));
        return ResponseEntity
                .created(URI.create("/v1/regras/" + criada.id()))
                .body(RegraResponse.de(criada));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Substitui integralmente uma regra")
    public ResponseEntity<RegraResponse> atualizar(@PathVariable UUID id,
                                                   @Valid @RequestBody RegraRequest requisicao) {
        return ResponseEntity.ok(RegraResponse.de(gerenciarRegras.atualizar(id, requisicao.paraDominio(id))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove uma regra")
    public ResponseEntity<Void> remover(@PathVariable UUID id) {
        gerenciarRegras.remover(id);
        return ResponseEntity.noContent().build();
    }
}
