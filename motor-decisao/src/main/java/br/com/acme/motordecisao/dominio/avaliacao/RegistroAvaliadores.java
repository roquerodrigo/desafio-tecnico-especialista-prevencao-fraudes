package br.com.acme.motordecisao.dominio.avaliacao;

import br.com.acme.motordecisao.dominio.regra.Operador;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * Registry de avaliadores por operador. O dispatch e <b>O(1)</b>.
 *
 * <p>Toda comparacao, inclusive igualdade, passa por {@link Comparable#compareTo} quando os tipos
 * permitem. Isso e essencial para {@link java.math.BigDecimal}: {@code new BigDecimal("300.00")}
 * nao e {@code equals} a {@code new BigDecimal("300")}, embora represente o mesmo valor. Usar
 * {@code equals} em igualdade monetaria faria uma condicao correta nunca ser satisfeita.
 *
 * <p>Quando o tipo nao suporta ordem — um booleano com {@code MAIOR_QUE}, por exemplo —, a
 * condicao nao e satisfeita, em vez de lancar excecao. Escolha deliberada: a validacao do
 * cadastro e apenas estrutural, portanto regras semanticamente inuteis podem existir, e uma
 * delas nao pode derrubar a avaliacao de uma transacao legitima.
 */
public final class RegistroAvaliadores {

    private static final Map<Operador, AvaliadorCondicao> AVALIADORES = criarRegistro();

    private RegistroAvaliadores() {
    }

    private static Map<Operador, AvaliadorCondicao> criarRegistro() {
        Map<Operador, AvaliadorCondicao> registro = new EnumMap<>(Operador.class);
        registro.put(Operador.IGUAL, RegistroAvaliadores::saoIguais);
        registro.put(Operador.DIFERENTE, (contexto, referencia) -> !saoIguais(contexto, referencia));
        registro.put(Operador.MAIOR_QUE, ordinal(resultado -> resultado > 0));
        registro.put(Operador.MAIOR_OU_IGUAL, ordinal(resultado -> resultado >= 0));
        registro.put(Operador.MENOR_QUE, ordinal(resultado -> resultado < 0));
        registro.put(Operador.MENOR_OU_IGUAL, ordinal(resultado -> resultado <= 0));
        return Map.copyOf(registro);
    }

    private static boolean saoIguais(Object contexto, Object referencia) {
        return comparar(contexto, referencia)
                .map(resultado -> resultado == 0)
                .orElseGet(() -> Objects.equals(contexto, referencia));
    }

    private static AvaliadorCondicao ordinal(IntPredicate aceita) {
        return (contexto, referencia) -> comparar(contexto, referencia)
                .map(aceita::test)
                .orElse(false);
    }

    /**
     * Compara dois valores quando ambos sao {@link Comparable} do mesmo tipo.
     *
     * <p>Booleanos sao deliberadamente excluidos da comparacao ordinal. {@link Boolean} implementa
     * {@code Comparable} — {@code Boolean.TRUE.compareTo(Boolean.FALSE)} devolve 1 —, o que faria
     * uma regra sem sentido como {@code CPF_EM_LISTA_PERMISSIVA MAIOR_QUE 5} ser acionada para
     * todo CPF em lista permissiva. Em antifraude esse e um falso positivo silencioso: a regra
     * pontuaria sem que ninguem tivesse pedido. Sobre booleano, apenas igualdade tem sentido.
     *
     * @return o resultado de {@code compareTo}, ou vazio quando a comparacao ordinal nao se aplica
     */
    private static java.util.Optional<Integer> comparar(Object contexto, Object referencia) {
        if (contexto instanceof Boolean || referencia instanceof Boolean) {
            return java.util.Optional.empty();
        }
        if (!(contexto instanceof Comparable<?>) || referencia == null) {
            return java.util.Optional.empty();
        }
        if (!contexto.getClass().isInstance(referencia)) {
            return java.util.Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Comparable<Object> alvo = (Comparable<Object>) contexto;
        return java.util.Optional.of(alvo.compareTo(referencia));
    }

    public static AvaliadorCondicao para(Operador operador) {
        AvaliadorCondicao avaliador = AVALIADORES.get(operador);
        if (avaliador == null) {
            throw new IllegalStateException("Nenhum avaliador registrado para o operador " + operador);
        }
        return avaliador;
    }
}
