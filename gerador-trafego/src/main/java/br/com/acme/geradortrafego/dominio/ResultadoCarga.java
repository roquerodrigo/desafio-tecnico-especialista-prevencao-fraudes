package br.com.acme.geradortrafego.dominio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Acumula as medicoes de uma execucao de carga.
 *
 * <p>Escrito concorrentemente por muitas virtual threads e lido pelo endpoint de relatorio a
 * qualquer momento, inclusive durante a execucao. Por isso os acumuladores sao atomicos e a lista
 * de amostras e sincronizada — a alternativa, sincronizar o objeto inteiro, transformaria o
 * acumulador em gargalo e distorceria a propria medicao.
 */
public final class ResultadoCarga {

    public static final long META_P95_MILISSEGUNDOS = 150;

    private final Instant inicio;
    private final int duracaoSegundosPlanejada;
    private final int requisicoesPorSegundoPlanejada;

    private final List<Long> latencias = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger sucessos = new AtomicInteger();
    private final AtomicInteger erros = new AtomicInteger();
    private final AtomicLong latenciaMaxima = new AtomicLong();
    private final Map<String, AtomicInteger> decisoes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> porTipoTransacao = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> errosPorMotivo = new ConcurrentHashMap<>();

    private volatile Situacao situacao = Situacao.EM_EXECUCAO;
    private volatile Instant fim;

    public ResultadoCarga(Instant inicio, int duracaoSegundosPlanejada, int requisicoesPorSegundoPlanejada) {
        this.inicio = inicio;
        this.duracaoSegundosPlanejada = duracaoSegundosPlanejada;
        this.requisicoesPorSegundoPlanejada = requisicoesPorSegundoPlanejada;
    }

    public void registrarSucesso(long latenciaMilissegundos, String decisao, String tipoTransacao) {
        latencias.add(latenciaMilissegundos);
        sucessos.incrementAndGet();
        latenciaMaxima.accumulateAndGet(latenciaMilissegundos, Math::max);
        decisoes.computeIfAbsent(decisao, chave -> new AtomicInteger()).incrementAndGet();
        porTipoTransacao.computeIfAbsent(tipoTransacao, chave -> new AtomicInteger()).incrementAndGet();
    }

    public void registrarErro(String motivo) {
        erros.incrementAndGet();
        errosPorMotivo.computeIfAbsent(motivo, chave -> new AtomicInteger()).incrementAndGet();
    }

    public void concluir(Situacao situacaoFinal) {
        this.fim = Instant.now();
        this.situacao = situacaoFinal;
    }

    /**
     * Fotografia consistente do estado atual. A copia da lista sob lock e necessaria porque
     * ordenar a lista compartilhada durante a carga corromperia as escritas concorrentes.
     */
    public Relatorio relatorio() {
        List<Long> ordenadas;
        synchronized (latencias) {
            ordenadas = new ArrayList<>(latencias);
        }
        Collections.sort(ordenadas);

        long p95 = CalculadoraPercentil.percentil(ordenadas, 95);

        return new Relatorio(
                situacao.name(),
                duracaoSegundosPlanejada,
                Duration.between(inicio, fim == null ? Instant.now() : fim).toMillis(),
                requisicoesPorSegundoPlanejada,
                new Requisicoes(sucessos.get() + erros.get(), sucessos.get(), erros.get()),
                new Latencia(
                        CalculadoraPercentil.percentil(ordenadas, 50),
                        p95,
                        CalculadoraPercentil.percentil(ordenadas, 99),
                        Math.round(CalculadoraPercentil.media(ordenadas) * 100) / 100.0,
                        latenciaMaxima.get()),
                copiar(decisoes),
                copiar(porTipoTransacao),
                copiar(errosPorMotivo),
                META_P95_MILISSEGUNDOS,
                !ordenadas.isEmpty() && p95 <= META_P95_MILISSEGUNDOS);
    }

    private Map<String, Integer> copiar(Map<String, AtomicInteger> origem) {
        Map<String, Integer> copia = new LinkedHashMap<>();
        origem.forEach((chave, valor) -> copia.put(chave, valor.get()));
        return copia;
    }

    public Situacao situacao() {
        return situacao;
    }

    public enum Situacao {
        EM_EXECUCAO,
        CONCLUIDA,
        INTERROMPIDA
    }

    public record Relatorio(
            String situacao,
            int duracaoSegundosPlanejada,
            long duracaoRealMilissegundos,
            int requisicoesPorSegundoPlanejada,
            Requisicoes requisicoes,
            Latencia latenciaMs,
            Map<String, Integer> decisoes,
            Map<String, Integer> porTipoTransacao,
            Map<String, Integer> errosPorMotivo,
            long metaP95Ms,
            boolean metaAtingida) {
    }

    public record Requisicoes(int total, int sucesso, int erro) {
    }

    public record Latencia(long p50, long p95, long p99, double media, long maxima) {
    }
}
