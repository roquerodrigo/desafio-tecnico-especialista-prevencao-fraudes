package br.com.acme.motordecisao.dominio;

import br.com.acme.motordecisao.dominio.avaliacao.AvaliadorRegra;
import br.com.acme.motordecisao.dominio.avaliacao.ContextoAvaliacao;
import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.dominio.regra.Escada;
import br.com.acme.motordecisao.dominio.regra.Regra;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula o score de risco a partir do conjunto efetivo de regras.
 *
 * <p>As duas naturezas contribuem de formas distintas:
 * <ul>
 *   <li><b>escadas</b> — exatamente uma faixa por escada, resolvida em O(log n)</li>
 *   <li><b>condicionais</b> — todas as que casam, cumulativas</li>
 * </ul>
 *
 * <p>O piso e aplicado <b>uma unica vez, ao final</b>, nunca a resultados intermediarios. A
 * distincao importa: com regras que somam 200 e subtraem 500, aplicar o piso a cada passo daria
 * 1 e depois 1; aplicar so ao final da -300 e depois 1. O enunciado exige o segundo
 * comportamento.
 *
 * <p>Complexidade total: <b>O(n log n + R × C)</b>, com n faixas por escada, R regras
 * condicionais e C condicoes por regra.
 */
public final class CalculadoraScore {

    private static final int PISO = 1;

    private CalculadoraScore() {
    }

    public static ResultadoScore calcular(ConjuntoEfetivo conjunto, ContextoAvaliacao contexto) {
        int somaAcumulada = 0;
        List<Regra> acionadas = new ArrayList<>();

        for (Escada escada : conjunto.escadas()) {
            Regra faixa = escada.resolver(contexto.valorTransacao());
            somaAcumulada += faixa.acao().pontosComSinal();
            acionadas.add(faixa);
        }

        for (Regra regra : conjunto.regrasCondicionais()) {
            if (AvaliadorRegra.acionada(regra, contexto)) {
                somaAcumulada += regra.acao().pontosComSinal();
                acionadas.add(regra);
            }
        }

        return new ResultadoScore(Math.max(PISO, somaAcumulada), List.copyOf(acionadas));
    }
}
