package br.com.acme.listas.infraestrutura.dynamodb;

/**
 * Tres tabelas, nao single-table design.
 *
 * <p>Single-table existe para colocar entidades <b>relacionadas</b> na mesma particao e evitar
 * join quando os padroes de acesso sao variados. Aqui sao tres dominios de lookup pontual e
 * independente: nao ha relacionamento nem join a eliminar.
 *
 * <p>O custo que se imaginaria — perder o round-trip unico — nao existe: {@code BatchGetItem}
 * aceita multiplas tabelas na mesma requisicao. E a separacao entrega o que single-table nao
 * daria: schemas distintos por tipo, TTL apenas onde faz sentido, e metricas independentes.
 */
public final class NomesTabelas {

    public static final String LISTAS_CPF = "listas_cpf";
    public static final String LISTAS_IP = "listas_ip";
    public static final String LISTAS_DISPOSITIVO = "listas_dispositivo";

    public static final String ATRIBUTO_TTL = "expiraEm";

    private NomesTabelas() {
    }
}
