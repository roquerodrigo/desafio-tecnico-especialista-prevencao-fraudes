package br.com.acme.analiserisco.infraestrutura.mensageria;

import br.com.acme.analiserisco.aplicacao.GerenciarFaixasUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublicadorEventoFaixas implements GerenciarFaixasUseCase.NotificadorAlteracaoFaixas {

    public static final String TOPICO = "faixas.atualizadas";

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicadorEventoFaixas.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PublicadorEventoFaixas(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * A falha de publicacao e registrada mas <b>nao propagada</b>. Neste ponto a configuracao ja foi
     * persistida e o cache local ja recarregou; propagar o erro faria o gestor concluir que a
     * alteracao falhou quando ela valeu.
     *
     * <p>A consequencia de perder o evento e limitada: outras replicas seguem com as faixas
     * anteriores ate a proxima alteracao, e o erro fica no log.
     */
    @Override
    public void notificar() {
        EventoFaixasAtualizadas evento = EventoFaixasAtualizadas.agora();
        try {
            kafkaTemplate.send(TOPICO, "faixas", evento)
                    .whenComplete((resultado, erro) -> {
                        if (erro != null) {
                            registrarFalha(erro);
                        }
                    });
        } catch (RuntimeException excecao) {
            registrarFalha(excecao);
        }
    }

    private void registrarFalha(Throwable erro) {
        LOGGER.error("Falha ao publicar em {}. A alteracao vale nesta instancia; outras replicas "
                + "podem seguir com faixas desatualizadas. Causa: {}", TOPICO, erro.getMessage());
    }
}
