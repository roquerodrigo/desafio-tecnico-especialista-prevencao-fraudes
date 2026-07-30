package br.com.acme.auditoria.aplicacao;

import br.com.acme.auditoria.dominio.TrilhaDecisao;
import br.com.acme.auditoria.infraestrutura.persistencia.TrilhaDecisaoEntity;
import br.com.acme.auditoria.infraestrutura.persistencia.TrilhaDecisaoJpaRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Persiste a trilha das decisoes consumidas do topico.
 *
 * <p>Idempotente: violacao da unicidade de correlacao e tratada como sucesso. O Kafka garante
 * entrega ao menos uma vez, portanto reentrega e esperada — e a alternativa, deixar a excecao subir,
 * faria o consumidor reprocessar em laco infinito.
 */
@Service
public class RegistrarTrilhaUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarTrilhaUseCase.class);

    private final TrilhaDecisaoJpaRepository repositorio;
    private final ObjectMapper objectMapper;

    public RegistrarTrilhaUseCase(TrilhaDecisaoJpaRepository repositorio, ObjectMapper objectMapper) {
        this.repositorio = repositorio;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registrar(TrilhaDecisao trilha) {
        if (repositorio.existsByIdCorrelacao(trilha.idCorrelacao())) {
            LOGGER.debug("Trilha de correlacao {} ja registrada; reentrega ignorada", trilha.idCorrelacao());
            return;
        }

        try {
            repositorio.save(new TrilhaDecisaoEntity(
                    trilha.id() == null ? UUID.randomUUID() : trilha.id(),
                    trilha.idCorrelacao(),
                    trilha.cpf(),
                    trilha.ip(),
                    trilha.idDispositivo(),
                    trilha.tipoTransacao(),
                    trilha.valorTransacao(),
                    trilha.score(),
                    trilha.classificacao(),
                    trilha.decisao(),
                    paraJson(trilha.regrasAcionadas()),
                    paraJson(trilha.resultadoListas()),
                    trilha.consultaListasDegradada(),
                    trilha.ocorridoEm()));

            LOGGER.info("Trilha registrada para a correlacao {}: score {}, classificacao {}, decisao {}",
                    trilha.idCorrelacao(), trilha.score(), trilha.classificacao(), trilha.decisao());
        } catch (DataIntegrityViolationException excecao) {
            LOGGER.debug("Trilha de correlacao {} registrada concorrentemente; tratando como sucesso",
                    trilha.idCorrelacao());
        }
    }

    @Transactional(readOnly = true)
    public List<TrilhaDecisaoEntity> consultarPorCpf(String cpf, int limite) {
        return repositorio.findByCpfOrderByOcorridoEmDesc(cpf, Limit.of(limite));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TrilhaDecisaoEntity> consultarPorCorrelacao(String idCorrelacao) {
        return repositorio.findByIdCorrelacao(idCorrelacao);
    }

    private String paraJson(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JacksonException excecao) {
            throw new IllegalStateException("Falha ao serializar o detalhamento da trilha", excecao);
        }
    }
}
