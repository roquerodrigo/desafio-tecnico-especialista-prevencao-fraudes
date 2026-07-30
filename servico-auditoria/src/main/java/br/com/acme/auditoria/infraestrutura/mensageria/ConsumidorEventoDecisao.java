package br.com.acme.auditoria.infraestrutura.mensageria;

import br.com.acme.auditoria.aplicacao.RegistrarTrilhaUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consome as decisoes e persiste a trilha.
 *
 * <p>Diferente dos consumidores de invalidacao de cache, este usa {@code group.id}
 * <b>compartilhado</b>: aqui o objetivo e distribuir o processamento, para que cada decisao seja
 * persistida uma unica vez. Fan-out produziria registros duplicados.
 *
 * <p>{@code auto-offset-reset} e {@code earliest}: nenhuma decisao pode ser perdida por reinicio
 * do consumidor.
 */
@Component
public class ConsumidorEventoDecisao {

    public static final String TOPICO = "decisoes.registradas";

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumidorEventoDecisao.class);

    private final RegistrarTrilhaUseCase registrarTrilha;

    public ConsumidorEventoDecisao(RegistrarTrilhaUseCase registrarTrilha) {
        this.registrarTrilha = registrarTrilha;
    }

    @KafkaListener(topics = TOPICO, groupId = "${app.kafka.grupo-trilha}")
    public void aoReceber(EventoDecisaoRegistrada evento) {
        MDC.put("idCorrelacao", evento.idCorrelacao());
        try {
            registrarTrilha.registrar(evento.paraDominio());
        } catch (RuntimeException excecao) {
            LOGGER.error("Falha ao registrar a trilha da correlacao {}: {}",
                    evento.idCorrelacao(), excecao.getMessage(), excecao);
            throw excecao;
        } finally {
            MDC.remove("idCorrelacao");
        }
    }
}
