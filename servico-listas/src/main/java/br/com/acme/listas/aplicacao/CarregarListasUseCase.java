package br.com.acme.listas.aplicacao;

import br.com.acme.listas.aplicacao.porta.RepositorioListas;
import br.com.acme.listas.dominio.EntradaLista;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Carga de entradas nas listas.
 *
 * <p>Idempotente por variavel: reenviar a mesma entrada sobrescreve, nao duplica. Nao existe
 * mecanismo de recarga porque nao existe cache local — o DynamoDB e fonte compartilhada e a leitura
 * e sempre atual. O requisito de "recarregar as listas quando atualizadas" e atendido por ausencia
 * de cache stale, nao por invalidacao.
 */
@Service
public class CarregarListasUseCase {

    private static final int MAXIMO_POR_LOTE = 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(CarregarListasUseCase.class);

    private final RepositorioListas repositorio;

    public CarregarListasUseCase(RepositorioListas repositorio) {
        this.repositorio = repositorio;
    }

    public int executar(List<EntradaLista> entradas) {
        if (entradas == null || entradas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma entrada");
        }
        if (entradas.size() > MAXIMO_POR_LOTE) {
            throw new IllegalArgumentException(
                    "Lote acima do limite de " + MAXIMO_POR_LOTE + " entradas; recebido: " + entradas.size());
        }

        repositorio.gravar(entradas);
        LOGGER.info("{} entradas de lista processadas", entradas.size());
        return entradas.size();
    }
}
