package br.com.acme.geradortrafego.aplicacao;

import br.com.acme.geradortrafego.aplicacao.porta.ClienteAnaliseRisco;
import br.com.acme.geradortrafego.dominio.PerfilTrafego;
import br.com.acme.geradortrafego.dominio.ResultadoCarga;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executa uma carga de trafego contra a API de analise de risco.
 *
 * <p><b>Uma carga por vez.</b> Duas execucoes simultaneas misturariam amostras de latencia e o p95
 * resultante nao descreveria nenhuma das duas. A segunda tentativa e rejeitada.
 *
 * <p><b>Virtual threads</b> para a concorrencia: a carga e limitada por I/O, e uma thread virtual
 * por requisicao sustenta centenas de chamadas em voo sem dimensionar pool a mao. Com threads de
 * plataforma, o pool viraria o gargalo e o gerador estaria medindo a si mesmo.
 *
 * <p>O ritmo e controlado por janelas de um segundo: a cada segundo dispara-se o lote configurado e
 * aguarda-se o restante da janela. E um controle simples e suficiente para demonstracao — nao
 * pretende ser um gerador de carga de precisao.
 */
@Service
public class ExecutarCargaUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutarCargaUseCase.class);
    private static final Duration JANELA = Duration.ofSeconds(1);

    private final ClienteAnaliseRisco cliente;
    private final AtomicReference<ResultadoCarga> cargaAtual = new AtomicReference<>();
    private final AtomicReference<Thread> execucao = new AtomicReference<>();

    public ExecutarCargaUseCase(ClienteAnaliseRisco cliente) {
        this.cliente = cliente;
    }

    public ResultadoCarga.Relatorio iniciar(int duracaoSegundos, int requisicoesPorSegundo, long semente) {
        validar(duracaoSegundos, requisicoesPorSegundo);

        ResultadoCarga resultado = new ResultadoCarga(Instant.now(), duracaoSegundos, requisicoesPorSegundo);
        if (!cargaAtual.compareAndSet(null, resultado)) {
            throw new CargaEmAndamentoException();
        }

        Thread thread = Thread.ofVirtual().name("carga-principal").start(
                () -> executar(resultado, duracaoSegundos, requisicoesPorSegundo, semente));
        execucao.set(thread);

        LOGGER.info("Carga iniciada: {} req/s por {}s", requisicoesPorSegundo, duracaoSegundos);
        return resultado.relatorio();
    }

    private void executar(ResultadoCarga resultado, int duracaoSegundos,
                          int requisicoesPorSegundo, long semente) {
        RandomGenerator sorteio = RandomGenerator.getDefault();
        if (semente != 0) {
            sorteio = new java.util.Random(semente);
        }
        PerfilTrafego perfil = new PerfilTrafego(sorteio);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int segundo = 0; segundo < duracaoSegundos; segundo++) {
                Instant inicioJanela = Instant.now();

                for (int requisicao = 0; requisicao < requisicoesPorSegundo; requisicao++) {
                    PerfilTrafego.TransacaoSintetica transacao = perfil.sortear();
                    executor.submit(() -> disparar(resultado, transacao));
                }

                if (Thread.currentThread().isInterrupted()) {
                    concluir(resultado, ResultadoCarga.Situacao.INTERROMPIDA);
                    return;
                }
                aguardarRestanteDaJanela(inicioJanela);
            }
        } catch (InterrompidaException excecao) {
            concluir(resultado, ResultadoCarga.Situacao.INTERROMPIDA);
            return;
        }

        concluir(resultado, ResultadoCarga.Situacao.CONCLUIDA);
    }

    private void disparar(ResultadoCarga resultado, PerfilTrafego.TransacaoSintetica transacao) {
        long inicio = System.nanoTime();
        try {
            ClienteAnaliseRisco.RespostaAnalise resposta = cliente.analisar(transacao);
            long latencia = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

            if (resposta.sucesso()) {
                resultado.registrarSucesso(latencia, resposta.decisao(), transacao.tipoTransacao());
            } else {
                resultado.registrarErro(resposta.motivoErro());
            }
        } catch (RuntimeException excecao) {
            resultado.registrarErro(excecao.getClass().getSimpleName());
        }
    }

    private void aguardarRestanteDaJanela(Instant inicioJanela) {
        Duration decorrido = Duration.between(inicioJanela, Instant.now());
        Duration restante = JANELA.minus(decorrido);
        if (restante.isNegative() || restante.isZero()) {
            return;
        }
        try {
            Thread.sleep(restante);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            throw new InterrompidaException();
        }
    }

    private void concluir(ResultadoCarga resultado, ResultadoCarga.Situacao situacao) {
        resultado.concluir(situacao);
        LOGGER.info("Carga {}: {}", situacao, resultado.relatorio().requisicoes());
        cargaAtual.compareAndSet(resultado, null);
        ultimoRelatorio.set(resultado.relatorio());
    }

    private final AtomicReference<ResultadoCarga.Relatorio> ultimoRelatorio = new AtomicReference<>();

    /**
     * Relatorio da carga em andamento ou, se nenhuma estiver ativa, o da ultima concluida.
     */
    public Optional<ResultadoCarga.Relatorio> relatorioAtual() {
        ResultadoCarga emAndamento = cargaAtual.get();
        if (emAndamento != null) {
            return Optional.of(emAndamento.relatorio());
        }
        return Optional.ofNullable(ultimoRelatorio.get());
    }

    public Optional<ResultadoCarga.Relatorio> interromper() {
        ResultadoCarga emAndamento = cargaAtual.get();
        if (emAndamento == null) {
            return Optional.empty();
        }
        Thread thread = execucao.get();
        if (thread != null) {
            thread.interrupt();
        }
        emAndamento.concluir(ResultadoCarga.Situacao.INTERROMPIDA);
        cargaAtual.compareAndSet(emAndamento, null);
        ultimoRelatorio.set(emAndamento.relatorio());
        return Optional.of(emAndamento.relatorio());
    }

    private void validar(int duracaoSegundos, int requisicoesPorSegundo) {
        if (duracaoSegundos <= 0 || duracaoSegundos > 600) {
            throw new IllegalArgumentException(
                    "duracaoSegundos deve estar entre 1 e 600; recebido: " + duracaoSegundos);
        }
        if (requisicoesPorSegundo <= 0 || requisicoesPorSegundo > 1000) {
            throw new IllegalArgumentException(
                    "requisicoesPorSegundo deve estar entre 1 e 1000; recebido: " + requisicoesPorSegundo);
        }
    }

    public static class CargaEmAndamentoException extends RuntimeException {
        public CargaEmAndamentoException() {
            super("Ja existe uma carga em andamento. Aguarde a conclusao ou interrompa antes de iniciar outra.");
        }
    }

    private static class InterrompidaException extends RuntimeException {
    }
}
