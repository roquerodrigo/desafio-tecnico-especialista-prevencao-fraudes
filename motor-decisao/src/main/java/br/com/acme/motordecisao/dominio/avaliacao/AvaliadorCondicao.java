package br.com.acme.motordecisao.dominio.avaliacao;

/**
 * Strategy de comparacao. Uma implementacao por operador.
 *
 * <p>Operador novo custa uma classe nova mais uma entrada no {@link RegistroAvaliadores} — o
 * avaliador nao muda. Um {@code switch} sobre enum concentraria a mudanca num ponto quente e
 * cresceria a cada operador.
 */
public interface AvaliadorCondicao {

    /**
     * @param valorDoContexto valor extraido da transacao, ja no tipo do campo
     * @param valorDeReferencia valor declarado na condicao, ja coagido
     */
    boolean avaliar(Object valorDoContexto, Object valorDeReferencia);
}
