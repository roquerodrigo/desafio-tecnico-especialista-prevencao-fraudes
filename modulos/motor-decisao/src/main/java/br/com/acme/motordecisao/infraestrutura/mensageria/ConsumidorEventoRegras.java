package br.com.acme.motordecisao.infraestrutura.mensageria;

import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Recarrega o cache local quando alguma replica altera regras.
 *
 * <p>O {@code group.id} e <b>unico por instancia</b> — veja {@code spring.kafka.consumer.group-id}
 * no {@code application.yml}, que inclui o hostname. Com group compartilhado, o Kafka entregaria
 * a mensagem a uma unica replica do grupo, e as demais seguiriam com cache velho. Invalidacao de
 * cache exige fan-out, nao distribuicao de carga.
 */
@Component
public class ConsumidorEventoRegras {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumidorEventoRegras.class);

    private final CacheRegras cacheRegras;

    public ConsumidorEventoRegras(CacheRegras cacheRegras) {
        this.cacheRegras = cacheRegras;
    }

    @KafkaListener(topics = PublicadorEventoRegras.TOPICO, groupId = "${app.kafka.grupo-cache-regras}")
    public void aoReceber(EventoRegrasAtualizadas evento) {
        LOGGER.info("Recebido {} para o tipo {}; recarregando cache de regras",
                evento.operacao(), evento.tipoTransacaoAfetado());
        cacheRegras.recarregar();
    }
}
