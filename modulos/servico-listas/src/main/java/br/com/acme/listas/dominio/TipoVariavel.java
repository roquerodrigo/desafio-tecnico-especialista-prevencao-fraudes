package br.com.acme.listas.dominio;

/**
 * Tipos de variavel consultavel. Cada um define em quais listas pode constar.
 *
 * <p>Somente CPF admite lista permissiva. IP e dispositivo constam apenas em listas restritivas —
 * regra do enunciado, aqui codificada no proprio tipo em vez de espalhada em validacoes.
 */
public enum TipoVariavel {

    CPF(true),
    IP(false),
    DISPOSITIVO(false);

    private final boolean admitePermissiva;

    TipoVariavel(boolean admitePermissiva) {
        this.admitePermissiva = admitePermissiva;
    }

    public boolean admitePermissiva() {
        return admitePermissiva;
    }

    /**
     * Somente IP admite prazo de validade: enderecos IP sao reatribuidos por CGNAT e DHCP, e
     * bloqueio perpetuo puniria quem herdar o endereco. CPF e dispositivo nao expiram.
     */
    public boolean admiteExpiracao() {
        return this == IP;
    }
}
