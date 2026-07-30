package br.com.acme.analiserisco.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.acme.analiserisco.aplicacao.porta.CalculoScorePort;
import br.com.acme.analiserisco.aplicacao.porta.ConsultaListasPort;
import br.com.acme.analiserisco.dominio.ClassificacaoRisco;
import br.com.acme.analiserisco.dominio.Decisao;
import br.com.acme.analiserisco.dominio.FaixaScore;
import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.TabelaFaixas;
import br.com.acme.analiserisco.dominio.Transacao;
import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalisarRiscoUseCaseTest {

    @Mock
    private ConsultaListasPort consultaListas;

    @Mock
    private CalculoScorePort calculoScore;

    @Mock
    private CacheFaixas cacheFaixas;

    @Mock
    private AnalisarRiscoUseCase.RegistradorDecisao registradorDecisao;

    private AnalisarRiscoUseCase analisarRisco;

    private static final Transacao TRANSACAO = new Transacao(
            "52998224725", "203.0.113.42",
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            "PIX", new BigDecimal("1500.00"));

    @BeforeEach
    void preparar() {
        analisarRisco = new AnalisarRiscoUseCase(consultaListas, calculoScore, cacheFaixas, registradorDecisao);
    }

    private void comTabelaPadrao() {
        when(cacheFaixas.tabela()).thenReturn(Optional.of(TabelaFaixas.construir(List.of(
                new FaixaScore(ClassificacaoRisco.BAIXO, 399, Decisao.APROVADA),
                new FaixaScore(ClassificacaoRisco.MEDIO, 699, Decisao.APROVADA),
                new FaixaScore(ClassificacaoRisco.ALTO, null, Decisao.NEGADA)))));
    }

    @Test
    @DisplayName("aprova quando o score cai em faixa de aprovacao")
    void aprovaScoreBaixo() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.de(false, false, false, false));
        when(calculoScore.calcular(any(), any()))
                .thenReturn(new CalculoScorePort.ResultadoCalculo(300, List.of()));
        comTabelaPadrao();

        AnalisarRiscoUseCase.ResultadoAnalise resultado = analisarRisco.executar(TRANSACAO);

        assertThat(resultado.decisao()).isEqualTo(Decisao.APROVADA);
        assertThat(resultado.classificacao()).isEqualTo("BAIXO");
        assertThat(resultado.score()).isEqualTo(300);
    }

    @Test
    @DisplayName("nega quando o score cai em faixa de negativa")
    void negaScoreAlto() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.de(false, true, false, false));
        when(calculoScore.calcular(any(), any()))
                .thenReturn(new CalculoScorePort.ResultadoCalculo(750, List.of()));
        comTabelaPadrao();

        AnalisarRiscoUseCase.ResultadoAnalise resultado = analisarRisco.executar(TRANSACAO);

        assertThat(resultado.decisao()).isEqualTo(Decisao.NEGADA);
        assertThat(resultado.classificacao()).isEqualTo("ALTO");
    }

    @Test
    @DisplayName("com listas degradadas, a analise prossegue e o motor ainda pontua por valor")
    void prosseguComListasDegradadas() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.semConsulta());
        when(calculoScore.calcular(any(), any()))
                .thenReturn(new CalculoScorePort.ResultadoCalculo(300, List.of()));
        comTabelaPadrao();

        AnalisarRiscoUseCase.ResultadoAnalise resultado = analisarRisco.executar(TRANSACAO);

        assertThat(resultado.decisao()).isEqualTo(Decisao.APROVADA);
        assertThat(resultado.resultadoListas().degradado()).isTrue();

        ArgumentCaptor<ResultadoListas> capturado = ArgumentCaptor.forClass(ResultadoListas.class);
        verify(calculoScore).calcular(any(), capturado.capture());
        assertThat(capturado.getValue().cpfEmListaRestritiva())
                .as("degradacao assume ausencia em todas as listas")
                .isFalse();
    }

    @Test
    @DisplayName("a degradacao das listas e registrada na trilha, nunca silenciosa")
    void degradacaoVaiParaTrilha() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.semConsulta());
        when(calculoScore.calcular(any(), any()))
                .thenReturn(new CalculoScorePort.ResultadoCalculo(300, List.of()));
        comTabelaPadrao();

        analisarRisco.executar(TRANSACAO);

        ArgumentCaptor<AnalisarRiscoUseCase.ResultadoAnalise> capturado =
                ArgumentCaptor.forClass(AnalisarRiscoUseCase.ResultadoAnalise.class);
        verify(registradorDecisao).registrar(any(), capturado.capture());
        assertThat(capturado.getValue().resultadoListas().degradado()).isTrue();
    }

    @Test
    @DisplayName("fail-closed: falha do motor propaga e nao produz decisao de negocio")
    void falhaDoMotorPropaga() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.de(false, false, false, false));
        when(calculoScore.calcular(any(), any()))
                .thenThrow(new AnaliseIndisponivelException("Motor de decisao indisponivel", null));

        assertThatThrownBy(() -> analisarRisco.executar(TRANSACAO))
                .isInstanceOf(AnaliseIndisponivelException.class);

        verify(registradorDecisao, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("sem tabela de faixas, a analise falha em vez de classificar arbitrariamente")
    void semTabelaDeFaixasFalha() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.de(false, false, false, false));
        when(calculoScore.calcular(any(), any()))
                .thenReturn(new CalculoScorePort.ResultadoCalculo(300, List.of()));
        when(cacheFaixas.tabela()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analisarRisco.executar(TRANSACAO))
                .isInstanceOf(AnaliseIndisponivelException.class)
                .hasMessageContaining("Tabela de faixas");
    }

    @Test
    @DisplayName("consulta as listas antes de calcular o score, propagando os sinais apurados")
    void ordemDaOrquestracao() {
        ResultadoListas comRestricao = ResultadoListas.de(false, true, false, false);
        when(consultaListas.consultar(any())).thenReturn(comRestricao);
        when(calculoScore.calcular(any(), any()))
                .thenReturn(new CalculoScorePort.ResultadoCalculo(700, List.of()));
        comTabelaPadrao();

        analisarRisco.executar(TRANSACAO);

        verify(calculoScore).calcular(TRANSACAO, comRestricao);
    }

    @Test
    @DisplayName("registra a trilha de toda analise concluida")
    void registraTrilha() {
        when(consultaListas.consultar(any())).thenReturn(ResultadoListas.de(false, false, false, false));
        when(calculoScore.calcular(any(), any())).thenReturn(new CalculoScorePort.ResultadoCalculo(
                450, List.of(new CalculoScorePort.RegraAcionada("faixa_valor_2", "faixa 2", "SOMAR", 400))));
        comTabelaPadrao();

        analisarRisco.executar(TRANSACAO);

        ArgumentCaptor<AnalisarRiscoUseCase.ResultadoAnalise> capturado =
                ArgumentCaptor.forClass(AnalisarRiscoUseCase.ResultadoAnalise.class);
        verify(registradorDecisao).registrar(any(), capturado.capture());

        assertThat(capturado.getValue().score()).isEqualTo(450);
        assertThat(capturado.getValue().classificacao()).isEqualTo("MEDIO");
        assertThat(capturado.getValue().regrasAcionadas()).hasSize(1);
    }
}
