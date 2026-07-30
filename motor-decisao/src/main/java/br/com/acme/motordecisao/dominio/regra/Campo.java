package br.com.acme.motordecisao.dominio.regra;

/**
 * Catalogo fechado dos campos avaliaveis por uma condicao.
 *
 * <p>O catalogo e fechado de proposito: campo desconhecido e rejeitado na desserializacao do
 * cadastro, e nao em producao no meio de uma transacao. Cada campo declara seu tipo, o que
 * permite coagir o valor textual da condicao uma unica vez.
 */
public enum Campo {

    VALOR_TRANSACAO(TipoCampo.DECIMAL),
    TIPO_TRANSACAO(TipoCampo.TEXTO),
    CPF_EM_LISTA_PERMISSIVA(TipoCampo.BOOLEANO),
    CPF_EM_LISTA_RESTRITIVA(TipoCampo.BOOLEANO),
    IP_EM_LISTA_RESTRITIVA(TipoCampo.BOOLEANO),
    DISPOSITIVO_EM_LISTA_RESTRITIVA(TipoCampo.BOOLEANO);

    private final TipoCampo tipo;

    Campo(TipoCampo tipo) {
        this.tipo = tipo;
    }

    public TipoCampo tipo() {
        return tipo;
    }

    public enum TipoCampo {
        DECIMAL,
        TEXTO,
        BOOLEANO
    }
}
