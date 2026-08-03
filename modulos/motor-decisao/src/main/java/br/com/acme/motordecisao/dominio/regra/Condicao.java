package br.com.acme.motordecisao.dominio.regra;

import java.util.Objects;

/**
 * Predicado elementar de uma regra condicional: um campo, um operador e um valor de referencia.
 *
 * <p>O valor e mantido como texto porque e assim que chega do cadastro. A coercao para o tipo
 * do campo acontece na avaliacao, guiada por {@link Campo#tipo()}.
 *
 * <p>A validacao aqui e estrutural, nao semantica: {@code CPF_EM_LISTA_PERMISSIVA MAIOR_QUE 5}
 * e aceito e simplesmente nunca sera acionado. Decisao registrada como premissa no README.
 */
public record Condicao(Campo campo, Operador operador, String valor) {

    public Condicao {
        Objects.requireNonNull(campo, "campo da condicao e obrigatorio");
        Objects.requireNonNull(operador, "operador da condicao e obrigatorio");
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("valor da condicao e obrigatorio");
        }
    }

    public static Condicao de(Campo campo, Operador operador, String valor) {
        return new Condicao(campo, operador, valor);
    }

    public static Condicao verdadeiro(Campo campo) {
        return new Condicao(campo, Operador.IGUAL, "true");
    }
}
