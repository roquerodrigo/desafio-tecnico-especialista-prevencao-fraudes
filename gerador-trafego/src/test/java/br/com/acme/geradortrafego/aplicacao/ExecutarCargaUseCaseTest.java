package br.com.acme.geradortrafego.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.acme.geradortrafego.aplicacao.porta.ClienteAnaliseRisco;
import br.com.acme.geradortrafego.dominio.PerfilTrafego;
import br.com.acme.geradortrafego.dominio.ResultadoCarga;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutarCargaUseCaseTest {

    /** Cliente dublado: registra o que recebeu e responde conforme configurado. */
    private static class ClienteDublado implements ClienteAnaliseRisco {

        private final List<PerfilTrafego.TransacaoSintetica> recebidas = new CopyOnWriteArrayList<>();
        private final AtomicInteger chamadas = new AtomicInteger();
        private final int falharACada;

        ClienteDublado(int falharACada) {
            this.falharACada = falharACada;
        }

        @Override
        public RespostaAnalise analisar(PerfilTrafego.TransacaoSintetica transacao) {
            recebidas.add(transacao);
            int chamada = chamadas.incrementAndGet();
            if (falharACada > 0 && chamada % falharACada == 0) {
                return RespostaAnalise.erro("HTTP_503");
            }
            return RespostaAnalise.sucesso(chamada % 3 == 0 ? "NEGADA" : "APROVADA");
        }
    }

    private static ResultadoCarga.Relatorio aguardarConclusao(ExecutarCargaUseCase useCase) {
        for (int tentativa = 0; tentativa < 100; tentativa++) {
            ResultadoCarga.Relatorio relatorio = useCase.relatorioAtual().orElseThrow();
            if (!"EM_EXECUCAO".equals(relatorio.situacao())) {
                return relatorio;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException excecao) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(excecao);
            }
        }
        throw new AssertionError("a carga nao concluiu no tempo esperado");
    }

    @Test
    @DisplayName("executa a carga e contabiliza sucessos, decisoes e tipos")
    void executaEContabiliza() {
        ClienteDublado cliente = new ClienteDublado(0);
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(cliente);

        useCase.iniciar(1, 20, 42L);
        ResultadoCarga.Relatorio relatorio = aguardarConclusao(useCase);

        assertThat(relatorio.situacao()).isEqualTo("CONCLUIDA");
        assertThat(relatorio.requisicoes().total()).isEqualTo(20);
        assertThat(relatorio.requisicoes().sucesso()).isEqualTo(20);
        assertThat(relatorio.requisicoes().erro()).isZero();
        assertThat(relatorio.decisoes()).containsKeys("APROVADA", "NEGADA");
        assertThat(relatorio.porTipoTransacao()).isNotEmpty();
    }

    @Test
    @DisplayName("erros sao dados da medicao, nao interrompem a carga")
    void errosNaoInterrompemACarga() {
        ClienteDublado cliente = new ClienteDublado(3);
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(cliente);

        useCase.iniciar(1, 30, 42L);
        ResultadoCarga.Relatorio relatorio = aguardarConclusao(useCase);

        assertThat(relatorio.requisicoes().total()).isEqualTo(30);
        assertThat(relatorio.requisicoes().erro()).isEqualTo(10);
        assertThat(relatorio.errosPorMotivo()).containsEntry("HTTP_503", 10);
        assertThat(relatorio.situacao()).isEqualTo("CONCLUIDA");
    }

    @Test
    @DisplayName("recusa segunda carga simultanea — amostras misturadas invalidariam o percentil")
    void recusaCargaSimultanea() {
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(new ClienteDublado(0));

        useCase.iniciar(5, 10, 42L);

        assertThatThrownBy(() -> useCase.iniciar(5, 10, 42L))
                .isInstanceOf(ExecutarCargaUseCase.CargaEmAndamentoException.class)
                .hasMessageContaining("Ja existe uma carga em andamento");

        useCase.interromper();
    }

    @Test
    @DisplayName("interromper encerra a carga e devolve o relatorio parcial")
    void interromperEncerra() {
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(new ClienteDublado(0));
        useCase.iniciar(60, 5, 42L);

        ResultadoCarga.Relatorio relatorio = useCase.interromper().orElseThrow();

        assertThat(relatorio.situacao()).isEqualTo("INTERROMPIDA");
        assertThat(useCase.relatorioAtual()).isPresent();
    }

    @Test
    @DisplayName("interromper sem carga em andamento nao faz nada")
    void interromperSemCarga() {
        assertThat(new ExecutarCargaUseCase(new ClienteDublado(0)).interromper()).isEmpty();
    }

    @Test
    @DisplayName("sem nenhuma carga executada, nao ha relatorio")
    void semRelatorioInicial() {
        assertThat(new ExecutarCargaUseCase(new ClienteDublado(0)).relatorioAtual()).isEmpty();
    }

    @Test
    @DisplayName("apos concluir, o relatorio da ultima carga continua consultavel")
    void relatorioPersisteAposConcluir() {
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(new ClienteDublado(0));
        useCase.iniciar(1, 5, 42L);
        aguardarConclusao(useCase);

        assertThat(useCase.relatorioAtual()).isPresent();
        assertThat(useCase.relatorioAtual().orElseThrow().situacao()).isEqualTo("CONCLUIDA");
    }

    @Test
    @DisplayName("a carga recebe transacoes validas e variadas do perfil")
    void recebeTransacoesDoPerfil() {
        ClienteDublado cliente = new ClienteDublado(0);
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(cliente);

        useCase.iniciar(1, 40, 42L);
        aguardarConclusao(useCase);

        assertThat(cliente.recebidas).hasSize(40);
        assertThat(cliente.recebidas)
                .allSatisfy(transacao -> {
                    assertThat(transacao.cpf()).hasSize(11);
                    assertThat(transacao.valorTransacao().signum()).isPositive();
                    assertThat(transacao.tipoTransacao()).isIn("PIX", "CARTAO", "TED");
                });
    }

    @Test
    @DisplayName("rejeita parametros fora dos limites")
    void rejeitaParametrosInvalidos() {
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(new ClienteDublado(0));

        assertThatThrownBy(() -> useCase.iniciar(0, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duracaoSegundos");
        assertThatThrownBy(() -> useCase.iniciar(601, 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.iniciar(10, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requisicoesPorSegundo");
        assertThatThrownBy(() -> useCase.iniciar(10, 1001, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("meta de p95 e avaliada contra os 150 ms do SC-001")
    void metaDeP95() {
        ExecutarCargaUseCase useCase = new ExecutarCargaUseCase(new ClienteDublado(0));
        useCase.iniciar(1, 10, 42L);
        ResultadoCarga.Relatorio relatorio = aguardarConclusao(useCase);

        assertThat(relatorio.metaP95Ms()).isEqualTo(150L);
        assertThat(relatorio.metaAtingida())
                .as("com cliente dublado a latencia e proxima de zero")
                .isTrue();
    }
}
