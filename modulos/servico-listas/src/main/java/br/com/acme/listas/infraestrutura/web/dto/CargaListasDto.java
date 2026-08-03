package br.com.acme.listas.infraestrutura.web.dto;

import br.com.acme.listas.dominio.EntradaLista;
import br.com.acme.listas.dominio.Pertinencia;
import br.com.acme.listas.dominio.TipoVariavel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class CargaListasDto {

    private CargaListasDto() {
    }

    public record Requisicao(
            @NotEmpty(message = "entradas e obrigatoria e nao pode ser vazia")
            @Valid
            List<EntradaRequisicao> entradas) {

        public List<EntradaLista> paraDominio() {
            return entradas.stream().map(EntradaRequisicao::paraDominio).toList();
        }
    }

    public record EntradaRequisicao(
            @NotNull(message = "tipo e obrigatorio")
            TipoVariavel tipo,

            @NotBlank(message = "valor e obrigatorio")
            String valor,

            @Valid PertinenciaRequisicao permissiva,
            @Valid PertinenciaRequisicao restritiva,
            Instant expiraEm) {

        public EntradaLista paraDominio() {
            return new EntradaLista(
                    tipo,
                    valor,
                    permissiva == null ? null : permissiva.paraDominio(expiraEm),
                    restritiva == null ? null : restritiva.paraDominio(expiraEm),
                    expiraEm);
        }
    }

    public record PertinenciaRequisicao(
            @NotBlank(message = "idLista e obrigatorio")
            String idLista,

            Pertinencia.Situacao situacao) {

        public Pertinencia paraDominio(Instant expiraEm) {
            return new Pertinencia(
                    idLista,
                    situacao == null ? Pertinencia.Situacao.ATIVA : situacao,
                    Instant.now(),
                    expiraEm);
        }
    }

    public record Resposta(int processadas, int ignoradas) {
    }
}
