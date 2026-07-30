package br.com.acme.listas.infraestrutura.web;

import br.com.acme.listas.aplicacao.ConsultarListasUseCase;
import br.com.acme.listas.infraestrutura.web.dto.ConsultaListasDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/consultas-listas")
@Tag(name = "Consulta de Listas", description = "Verifica presença de CPF, IP e dispositivo nas listas")
public class ConsultaListasController {

    private final ConsultarListasUseCase consultarListas;

    public ConsultaListasController(ConsultarListasUseCase consultarListas) {
        this.consultarListas = consultarListas;
    }

    @PostMapping
    @Operation(summary = "Consulta as três variáveis em uma única chamada",
            description = "Pertinência nula significa que a variável não consta na lista. Entrada inativa "
                    + "ou expirada é reportada como ausente. Usa POST, e não GET, porque os identificadores "
                    + "são dados pessoais e não devem trafegar em query string.")
    public ResponseEntity<ConsultaListasDto.Resposta> consultar(
            @Valid @RequestBody ConsultaListasDto.Requisicao requisicao) {

        return ResponseEntity.ok(ConsultaListasDto.Resposta.de(
                consultarListas.executar(requisicao.cpf(), requisicao.ip(), requisicao.idDispositivo())));
    }
}
