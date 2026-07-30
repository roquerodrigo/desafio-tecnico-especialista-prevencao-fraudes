package br.com.acme.analiserisco.infraestrutura.mensageria;

import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Recarrega as faixas quando outra replica altera a configuracao.
 *
 * <p>{@code group.id} unico por instancia, pelo mesmo motivo do cache de regras: invalidacao exige
 * fan-out. Sem isso, replicas divergiriam e duas instancias classificariam o mesmo score de formas
 * diferentes.
 */
@Component
public class ConsumidorEventoFaixas {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumidorEventoFaixas.class);

    private final CacheFaixas cacheFaixas;

    public ConsumidorEventoFaixas(CacheFaixas cacheFaixas) {
        this.cacheFaixas = cacheFaixas;
    }

    @KafkaListener(topics = PublicadorEventoFaixas.TOPICO, groupId = "${app.kafka.grupo-cache-faixas}")
    public void aoReceber(EventoFaixasAtualizadas evento) {
        LOGGER.info("Faixas de score alteradas em {}; recarregando cache", evento.ocorridoEm());
        cacheFaixas.recarregar();
    }
}
