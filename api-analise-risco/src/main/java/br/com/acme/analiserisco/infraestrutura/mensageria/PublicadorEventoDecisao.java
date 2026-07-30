package br.com.acme.analiserisco.infraestrutura.mensageria;

import br.com.acme.analiserisco.aplicacao.AnalisarRiscoUseCase;
import br.com.acme.analiserisco.dominio.Transacao;
import br.com.acme.analiserisco.infraestrutura.observabilidade.MascaradorDadosSensiveis;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica a trilha da decisao, <b>fora do caminho critico</b> da resposta.
 *
 * <p>Falha na publicacao e registrada em log mas nao propagada: a decisao ja foi tomada, e negar a
 * transacao por falha de auditoria transformaria um problema de observabilidade em problema de
 * negocio (FR-035).
 */
@Component
public class PublicadorEventoDecisao implements AnalisarRiscoUseCase.RegistradorDecisao {

    public static final String TOPICO = "decisoes.registradas";

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicadorEventoDecisao.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PublicadorEventoDecisao(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void registrar(Transacao transacao, AnalisarRiscoUseCase.ResultadoAnalise resultado) {
        String idCorrelacao = MDC.get("idCorrelacao");
        EventoDecisaoRegistrada evento = new EventoDecisaoRegistrada(
                UUID.randomUUID(),
                idCorrelacao == null ? UUID.randomUUID().toString() : idCorrelacao,
                Instant.now(),
                transacao.cpf(),
                transacao.ip(),
                transacao.idDispositivo().toString(),
                transacao.tipoTransacao(),
                transacao.valorTransacao(),
                resultado.score(),
                resultado.classificacao(),
                resultado.decisao().name(),
                resultado.resultadoListas().degradado(),
                resultado.regrasAcionadas().stream()
                        .map(regra -> new EventoDecisaoRegistrada.RegraAcionada(
                                regra.chave(), regra.descricao(), regra.acao(), regra.pontos()))
                        .toList(),
                new EventoDecisaoRegistrada.ResultadoListas(
                        resultado.resultadoListas().cpfEmListaPermissiva(),
                        resultado.resultadoListas().cpfEmListaRestritiva(),
                        resultado.resultadoListas().ipEmListaRestritiva(),
                        resultado.resultadoListas().dispositivoEmListaRestritiva()));

        try {
            kafkaTemplate.send(TOPICO, evento.idCorrelacao(), evento)
                    .whenComplete((resultadoEnvio, erro) -> {
                        if (erro != null) {
                            registrarFalha(transacao, erro);
                        }
                    });
        } catch (RuntimeException excecao) {
            registrarFalha(transacao, excecao);
        }
    }

    private void registrarFalha(Transacao transacao, Throwable erro) {
        LOGGER.error("Falha ao publicar a trilha da decisao para o CPF {}. A decisao foi entregue "
                        + "ao cliente normalmente; a trilha desta analise sera perdida. Causa: {}",
                MascaradorDadosSensiveis.cpf(transacao.cpf()), erro.getMessage());
    }
}
