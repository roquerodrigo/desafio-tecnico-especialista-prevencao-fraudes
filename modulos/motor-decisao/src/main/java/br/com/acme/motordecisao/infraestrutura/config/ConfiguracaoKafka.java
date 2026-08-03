package br.com.acme.motordecisao.infraestrutura.config;

import br.com.acme.motordecisao.infraestrutura.mensageria.PublicadorEventoRegras;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class ConfiguracaoKafka {

    /**
     * A autoconfiguracao do Spring Boot expoe {@code KafkaTemplate<?, ?>} e
     * {@code ProducerFactory<?, ?>}, que nao satisfazem injecao com generics concretos. Construir a
     * factory a partir das propriedades ja resolvidas mantem a configuracao do
     * {@code application.yml} como fonte unica e evita recorrer a raw types.
     */
    @Bean
    KafkaTemplate<String, Object> kafkaTemplateEventos(KafkaProperties propriedades) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                propriedades.buildProducerProperties()));
    }

    /**
     * Uma particao basta: o volume de eventos de configuracao e desprezivel e uma unica particao
     * garante ordenacao total.
     */
    @Bean
    NewTopic topicoRegrasAtualizadas() {
        return TopicBuilder.name(PublicadorEventoRegras.TOPICO)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
