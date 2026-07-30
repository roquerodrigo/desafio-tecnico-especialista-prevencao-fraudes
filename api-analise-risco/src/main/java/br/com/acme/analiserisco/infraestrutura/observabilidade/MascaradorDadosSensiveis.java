package br.com.acme.analiserisco.infraestrutura.observabilidade;

/**
 * Mascara dados pessoais para registro em log.
 *
 * <p>Aplica-se a <b>log</b>, nao a trilha de auditoria. Log trafega para agregacao com retencao
 * ampla e muitos leitores; a trilha e base de investigacao de fraude, com acesso controlado, e
 * precisa do dado completo. Publicos diferentes, controles diferentes.
 *
 * <p>O mascaramento preserva prefixo e sufixo suficientes para correlacionar visualmente sem
 * permitir reidentificacao a partir do log.
 */
public final class MascaradorDadosSensiveis {

    private static final String OCULTO = "***";

    private MascaradorDadosSensiveis() {
    }

    /**
     * {@code 52998224725} vira {@code 529****4725}.
     */
    public static String cpf(String cpf) {
        if (cpf == null || cpf.length() != 11) {
            return OCULTO;
        }
        return cpf.substring(0, 3) + "****" + cpf.substring(7);
    }

    /**
     * Oculta o ultimo octeto de IPv4 e a segunda metade de IPv6. O prefixo preservado ainda
     * permite identificar rede de origem em investigacao operacional.
     */
    public static String ip(String ip) {
        if (ip == null || ip.isBlank()) {
            return OCULTO;
        }
        if (ip.contains(":")) {
            String[] grupos = ip.split(":");
            int preservados = Math.max(1, grupos.length / 2);
            return String.join(":", java.util.Arrays.copyOfRange(grupos, 0, preservados)) + ":" + OCULTO;
        }
        int ultimoPonto = ip.lastIndexOf('.');
        if (ultimoPonto < 0) {
            return OCULTO;
        }
        return ip.substring(0, ultimoPonto + 1) + OCULTO;
    }

    /**
     * Preserva os 8 primeiros caracteres do UUID — suficiente para correlacionar dentro de uma
     * janela de investigacao, insuficiente para reconstruir o identificador.
     */
    public static String idDispositivo(String idDispositivo) {
        if (idDispositivo == null || idDispositivo.length() < 8) {
            return OCULTO;
        }
        return idDispositivo.substring(0, 8) + "-" + OCULTO;
    }
}
