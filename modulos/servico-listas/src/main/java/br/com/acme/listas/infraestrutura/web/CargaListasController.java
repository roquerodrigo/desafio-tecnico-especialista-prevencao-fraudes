package br.com.acme.listas.infraestrutura.web;

import br.com.acme.listas.aplicacao.CarregarListasUseCase;
import br.com.acme.listas.infraestrutura.web.dto.CargaListasDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/listas")
@Tag(name = "Carga de Listas (administrativo)", description = "Inclusão e atualização de entradas nas listas")
public class CargaListasController {

    private final CarregarListasUseCase carregarListas;

    public CargaListasController(CarregarListasUseCase carregarListas) {
        this.carregarListas = carregarListas;
    }

    @PutMapping
    @Operation(summary = "Carrega ou atualiza entradas",
            description = "Idempotente por variável: reenviar a mesma entrada sobrescreve, não duplica. "
                    + "O efeito é imediato nas consultas seguintes — não há cache local a invalidar.")
    public ResponseEntity<CargaListasDto.Resposta> carregar(
            @Valid @RequestBody CargaListasDto.Requisicao requisicao) {

        int processadas = carregarListas.executar(requisicao.paraDominio());
        return ResponseEntity.ok(new CargaListasDto.Resposta(processadas, 0));
    }
}
