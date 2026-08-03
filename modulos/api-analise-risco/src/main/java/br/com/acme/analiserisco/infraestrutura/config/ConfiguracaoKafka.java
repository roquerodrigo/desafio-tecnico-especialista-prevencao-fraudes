package br.com.acme.analiserisco.infraestrutura.config;

import br.com.acme.analiserisco.infraestrutura.mensageria.PublicadorEventoDecisao;
import br.com.acme.analiserisco.infraestrutura.mensageria.PublicadorEventoFaixas;
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
     * Uma particao: volume desprezivel e ordenacao total desejavel para invalidacao.
     */
    @Bean
    NewTopic topicoFaixasAtualizadas() {
        return TopicBuilder.name(PublicadorEventoFaixas.TOPICO)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Tres particoes: aqui o objetivo e distribuir o processamento da trilha, nao fazer fan-out.
     * A chave do registro e o identificador de correlacao, o que preserva a ordem por transacao.
     */
    @Bean
    NewTopic topicoDecisoesRegistradas() {
        return TopicBuilder.name(PublicadorEventoDecisao.TOPICO)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
