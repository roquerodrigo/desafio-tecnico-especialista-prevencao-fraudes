package br.com.acme.motordecisao.dominio.avaliacao;

import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.OperadorLogico;
import br.com.acme.motordecisao.dominio.regra.Regra;

/**
 * Composite que reduz as condicoes de uma regra a um unico booleano.
 *
 * <p>E aqui que o requisito "regra com operador OU aplica sua acao uma unica vez" e satisfeito
 * estruturalmente: a composicao devolve <b>um</b> booleano, e quem consome aplica a acao uma vez.
 * Nao ha como somar pontos por condicao satisfeita, porque as condicoes individuais nao chegam a
 * quem pontua.
 *
 * <p>Complexidade: <b>O(C)</b> em condicoes, com dispatch O(1) por condicao. Ambos os operadores
 * usam curto-circuito.
 */
public final class AvaliadorRegra {

    private AvaliadorRegra() {
    }

    public static boolean acionada(Regra regra, ContextoAvaliacao contexto) {
        if (regra.ehEscada()) {
            throw new IllegalArgumentException(
                    "Regra de escada nao e avaliada por condicoes; use Escada.resolver. Regra: " + regra);
        }
        return regra.operadorLogico() == OperadorLogico.OU
                ? algumaSatisfeita(regra, contexto)
                : todasSatisfeitas(regra, contexto);
    }

    private static boolean todasSatisfeitas(Regra regra, ContextoAvaliacao contexto) {
        for (Condicao condicao : regra.condicoes()) {
            if (!satisfeita(condicao, contexto)) {
                return false;
            }
        }
        return true;
    }

    private static boolean algumaSatisfeita(Regra regra, ContextoAvaliacao contexto) {
        for (Condicao condicao : regra.condicoes()) {
            if (satisfeita(condicao, contexto)) {
                return true;
            }
        }
        return false;
    }

    private static boolean satisfeita(Condicao condicao, ContextoAvaliacao contexto) {
        Object valorDoContexto = RegistroExtratoresCampo.extrair(condicao.campo(), contexto);
        Object valorDeReferencia = RegistroExtratoresCampo.coagir(condicao.campo(), condicao.valor());
        return RegistroAvaliadores.para(condicao.operador())
                .avaliar(valorDoContexto, valorDeReferencia);
    }
}
