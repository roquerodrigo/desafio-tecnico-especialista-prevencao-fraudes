package br.com.acme.motordecisao.dominio.avaliacao;

import br.com.acme.motordecisao.dominio.regra.Campo;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry que resolve o valor de cada campo avaliavel a partir do contexto. Acesso <b>O(1)</b>.
 *
 * <p>Sem reflexao, de proposito: reflexao no caminho critico custa latencia e transfere o erro
 * de digitacao do cadastro para a producao. Com o enum {@link Campo}, campo invalido e rejeitado
 * na desserializacao. Campo novo custa uma constante e uma entrada neste mapa.
 */
public final class RegistroExtratoresCampo {

    private static final Map<Campo, Function<ContextoAvaliacao, Object>> EXTRATORES = criarRegistro();

    private RegistroExtratoresCampo() {
    }

    private static Map<Campo, Function<ContextoAvaliacao, Object>> criarRegistro() {
        Map<Campo, Function<ContextoAvaliacao, Object>> registro = new EnumMap<>(Campo.class);
        registro.put(Campo.VALOR_TRANSACAO, ContextoAvaliacao::valorTransacao);
        registro.put(Campo.TIPO_TRANSACAO, ContextoAvaliacao::tipoTransacao);
        registro.put(Campo.CPF_EM_LISTA_PERMISSIVA, ContextoAvaliacao::cpfEmListaPermissiva);
        registro.put(Campo.CPF_EM_LISTA_RESTRITIVA, ContextoAvaliacao::cpfEmListaRestritiva);
        registro.put(Campo.IP_EM_LISTA_RESTRITIVA, ContextoAvaliacao::ipEmListaRestritiva);
        registro.put(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA, ContextoAvaliacao::dispositivoEmListaRestritiva);
        return Map.copyOf(registro);
    }

    public static Object extrair(Campo campo, ContextoAvaliacao contexto) {
        Function<ContextoAvaliacao, Object> extrator = EXTRATORES.get(campo);
        if (extrator == null) {
            throw new IllegalStateException("Nenhum extrator registrado para o campo " + campo);
        }
        return extrator.apply(contexto);
    }

    /**
     * Coage o valor textual da condicao para o tipo do campo, uma unica vez por avaliacao.
     *
     * <p>Valor incoerente com o tipo devolve {@code null}, o que faz a condicao nao ser
     * satisfeita. Coerente com a decisao de validar apenas estrutura no cadastro.
     */
    public static Object coagir(Campo campo, String valor) {
        try {
            return switch (campo.tipo()) {
                case DECIMAL -> new BigDecimal(valor);
                case BOOLEANO -> Boolean.valueOf(valor);
                case TEXTO -> valor.trim().toUpperCase();
            };
        } catch (NumberFormatException excecao) {
            return null;
        }
    }
}
