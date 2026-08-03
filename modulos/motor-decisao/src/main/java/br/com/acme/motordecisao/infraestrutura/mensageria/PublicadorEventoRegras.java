package br.com.acme.motordecisao.infraestrutura.mensageria;

import br.com.acme.motordecisao.aplicacao.GerenciarRegrasUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PublicadorEventoRegras implements GerenciarRegrasUseCase.NotificadorAlteracaoRegras {

    public static final String TOPICO = "regras.atualizadas";

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicadorEventoRegras.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PublicadorEventoRegras(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * A falha de publicacao e registrada mas <b>nao propagada</b>. Neste ponto a regra ja foi
     * persistida com sucesso; propagar o erro faria o cliente concluir que a operacao falhou quando
     * ela nao falhou, e provavelmente reenviar — gerando conflito de chave duplicada.
     *
     * <p>A consequencia de perder o evento e limitada e observavel: as demais replicas seguem com
     * cache anterior ate a proxima alteracao. Quem atendeu a requisicao recarrega seu proprio cache
     * de forma sincrona, portanto responde correto imediatamente.
     */
    @Override
    public void notificar(String tipoTransacaoAfetado, GerenciarRegrasUseCase.OperacaoRegra operacao) {
        EventoRegrasAtualizadas evento = EventoRegrasAtualizadas.de(tipoTransacaoAfetado, operacao.name());
        try {
            kafkaTemplate.send(TOPICO, tipoTransacaoAfetado, evento)
                    .whenComplete((resultado, erro) -> {
                        if (erro != null) {
                            registrarFalha(operacao, erro);
                        } else {
                            LOGGER.info("Evento {} publicado para o tipo {}", operacao, tipoTransacaoAfetado);
                        }
                    });
        } catch (RuntimeException excecao) {
            registrarFalha(operacao, excecao);
        }
    }

    private void registrarFalha(GerenciarRegrasUseCase.OperacaoRegra operacao, Throwable erro) {
        LOGGER.error("Falha ao publicar {} em {}. A alteracao foi persistida e vale nesta instancia; "
                        + "outras replicas podem servir regras desatualizadas ate a proxima alteracao. Causa: {}",
                operacao, TOPICO, erro.getMessage());
    }
}
