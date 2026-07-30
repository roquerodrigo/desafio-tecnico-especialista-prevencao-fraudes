package br.com.acme.listas.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entrada a ser carregada nas listas. Valida na construcao a coerencia entre tipo e pertinencias.
 */
public record EntradaLista(
        TipoVariavel tipo,
        String valor,
        Pertinencia permissiva,
        Pertinencia restritiva,
        Instant expiraEm) {

    public EntradaLista {
        Objects.requireNonNull(tipo, "tipo e obrigatorio");
        List<String> problemas = new ArrayList<>();

        if (valor == null || valor.isBlank()) {
            problemas.add("valor: valor da variavel e obrigatorio");
        } else if (tipo == TipoVariavel.CPF && !ValidadorCpf.valido(valor)) {
            problemas.add("valor: CPF invalido");
        }

        if (permissiva != null && !tipo.admitePermissiva()) {
            problemas.add("permissiva: apenas CPF admite lista permissiva");
        }
        if (permissiva == null && restritiva == null) {
            problemas.add("pertinencia: informe ao menos uma lista");
        }
        if (expiraEm != null && !tipo.admiteExpiracao()) {
            problemas.add("expiraEm: apenas IP admite prazo de validade");
        }

        if (!problemas.isEmpty()) {
            throw new EntradaListaInvalidaException(problemas);
        }
    }
}
