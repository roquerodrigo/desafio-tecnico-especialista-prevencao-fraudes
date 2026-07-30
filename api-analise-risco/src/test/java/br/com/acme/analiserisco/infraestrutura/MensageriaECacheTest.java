package br.com.acme.analiserisco.infraestrutura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.acme.analiserisco.aplicacao.AnalisarRiscoUseCase;
import br.com.acme.analiserisco.aplicacao.porta.CalculoScorePort;
import br.com.acme.analiserisco.dominio.ClassificacaoRisco;
import br.com.acme.analiserisco.dominio.Decisao;
import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.Transacao;
import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import br.com.acme.analiserisco.infraestrutura.mensageria.ConsumidorEventoFaixas;
import br.com.acme.analiserisco.infraestrutura.mensageria.EventoFaixasAtualizadas;
import br.com.acme.analiserisco.infraestrutura.mensageria.PublicadorEventoDecisao;
import br.com.acme.analiserisco.infraestrutura.mensageria.PublicadorEventoFaixas;
import br.com.acme.analiserisco.infraestrutura.persistencia.FaixaScoreEntity;
import br.com.acme.analiserisco.infraestrutura.persistencia.FaixaScoreJpaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class MensageriaECacheTest {

    private static final Transacao TRANSACAO = new Transacao(
            "52998224725", "203.0.113.42",
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            "PIX", new BigDecimal("1500.00"));

    private static AnalisarRiscoUseCase.ResultadoAnalise resultado(boolean degradado) {
        return new AnalisarRiscoUseCase.ResultadoAnalise(
                Decisao.APROVADA, 450, ClassificacaoRisco.MEDIO.name(),
                List.of(new CalculoScorePort.RegraAcionada("faixa_valor_2", "faixa 2", "SOMAR", 400)),
                degradado ? ResultadoListas.semConsulta() : ResultadoListas.de(false, true, false, false));
    }

    @Nested
    @DisplayName("publicacao da trilha de decisao")
    class TrilhaDeDecisao {

        @Mock
        private KafkaTemplate<String, Object> kafkaTemplate;

        @Test
        @DisplayName("publica o evento com score, classificacao e regras acionadas")
        void publicaEventoCompleto() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            new PublicadorEventoDecisao(kafkaTemplate).registrar(TRANSACAO, resultado(false));

            verify(kafkaTemplate).send(eq(PublicadorEventoDecisao.TOPICO), anyString(), any());
        }

        @Test
        @DisplayName("falha na publicacao nao propaga — a decisao ja foi entregue ao cliente")
        void falhaNaPublicacaoNaoPropaga() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new org.springframework.kafka.KafkaException("broker fora"));

            assertThatCode(() -> new PublicadorEventoDecisao(kafkaTemplate)
                    .registrar(TRANSACAO, resultado(false)))
                    .as("FR-035: indisponibilidade da auditoria nao pode impedir a entrega da decisao")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("falha assincrona tambem nao propaga")
        void falhaAssincronaNaoPropaga() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

            assertThatCode(() -> new PublicadorEventoDecisao(kafkaTemplate)
                    .registrar(TRANSACAO, resultado(true)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("publicacao de alteracao de faixas")
    class AlteracaoDeFaixas {

        @Mock
        private KafkaTemplate<String, Object> kafkaTemplate;

        @Test
        @DisplayName("publica o sinal de invalidacao")
        void publicaSinal() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            new PublicadorEventoFaixas(kafkaTemplate).notificar();

            verify(kafkaTemplate).send(eq(PublicadorEventoFaixas.TOPICO), eq("faixas"), any());
        }

        @Test
        @DisplayName("falha na publicacao nao derruba a alteracao ja persistida")
        void falhaNaoPropaga() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenThrow(new org.springframework.kafka.KafkaException("broker fora"));

            assertThatCode(() -> new PublicadorEventoFaixas(kafkaTemplate).notificar())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("falha assincrona nao propaga")
        void falhaAssincronaNaoPropaga() {
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));

            assertThatCode(() -> new PublicadorEventoFaixas(kafkaTemplate).notificar())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("evento carrega apenas o sinal, sem transportar a configuracao")
        void eventoEhApenasSinal() {
            EventoFaixasAtualizadas evento = EventoFaixasAtualizadas.agora();

            assertThat(evento.idEvento()).isNotNull();
            assertThat(evento.ocorridoEm()).isNotNull();
        }
    }

    @Nested
    @DisplayName("consumo do evento de faixas")
    class ConsumoDeFaixas {

        @Mock
        private CacheFaixas cacheFaixas;

        @Test
        @DisplayName("recarrega o cache ao receber o evento")
        void recarregaAoReceber() {
            new ConsumidorEventoFaixas(cacheFaixas).aoReceber(EventoFaixasAtualizadas.agora());

            verify(cacheFaixas).recarregar();
        }
    }

    @Nested
    @DisplayName("cache de faixas")
    class Cache {

        @Mock
        private FaixaScoreJpaRepository repositorio;

        @Test
        @DisplayName("carrega a tabela a partir do banco")
        void carregaDoBanco() {
            when(repositorio.findAllByOrderByOrdemAsc()).thenReturn(List.of(
                    new FaixaScoreEntity(UUID.randomUUID(), "BAIXO", 399, "APROVADA", 1),
                    new FaixaScoreEntity(UUID.randomUUID(), "MEDIO", 699, "APROVADA", 2),
                    new FaixaScoreEntity(UUID.randomUUID(), "ALTO", null, "NEGADA", 3)));

            CacheFaixas cache = new CacheFaixas(repositorio);

            assertThat(cache.carregado()).isFalse();
            assertThat(cache.recarregar()).isTrue();
            assertThat(cache.carregado()).isTrue();
            assertThat(cache.tabela()).isPresent();
            assertThat(cache.tabela().orElseThrow().classificar(500).classificacao())
                    .isEqualTo(ClassificacaoRisco.MEDIO);
        }

        @Test
        @DisplayName("carga invalida preserva o estado anterior, nunca serve tabela incompleta")
        void cargaInvalidaPreservaEstadoAnterior() {
            when(repositorio.findAllByOrderByOrdemAsc())
                    .thenReturn(List.of(
                            new FaixaScoreEntity(UUID.randomUUID(), "BAIXO", 399, "APROVADA", 1),
                            new FaixaScoreEntity(UUID.randomUUID(), "ALTO", null, "NEGADA", 2)))
                    .thenReturn(List.of(
                            new FaixaScoreEntity(UUID.randomUUID(), "BAIXO", 399, "APROVADA", 1)));

            CacheFaixas cache = new CacheFaixas(repositorio);
            assertThat(cache.recarregar()).isTrue();

            assertThat(cache.recarregar())
                    .as("segunda carga viola a invariante de faixa sem teto")
                    .isFalse();
            assertThat(cache.carregado())
                    .as("o estado anterior valido deve continuar sendo servido")
                    .isTrue();
            assertThat(cache.tabela().orElseThrow().classificar(1000).decisao())
                    .isEqualTo(Decisao.NEGADA);
        }

        @Test
        @DisplayName("banco vazio nao produz tabela")
        void bancoVazio() {
            when(repositorio.findAllByOrderByOrdemAsc()).thenReturn(List.of());

            CacheFaixas cache = new CacheFaixas(repositorio);

            assertThat(cache.recarregar()).isFalse();
            assertThat(cache.tabela()).isEmpty();
        }
    }
}
