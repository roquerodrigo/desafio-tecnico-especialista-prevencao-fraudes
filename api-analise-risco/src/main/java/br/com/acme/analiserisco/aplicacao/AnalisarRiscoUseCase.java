package br.com.acme.analiserisco.aplicacao;

import br.com.acme.analiserisco.aplicacao.porta.CalculoScorePort;
import br.com.acme.analiserisco.aplicacao.porta.ConsultaListasPort;
import br.com.acme.analiserisco.dominio.Decisao;
import br.com.acme.analiserisco.dominio.FaixaScore;
import br.com.acme.analiserisco.dominio.ResultadoListas;
import br.com.acme.analiserisco.dominio.TabelaFaixas;
import br.com.acme.analiserisco.dominio.Transacao;
import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orquestra a analise de risco: consulta listas, obtem score, classifica e decide.
 *
 * <p>A ordem dos passos e a diferenca de tratamento de falha entre eles sao a essencia deste caso
 * de uso. Listas degradam; score nao.
 */
@Service
public class AnalisarRiscoUseCase {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalisarRiscoUseCase.class);

    private final ConsultaListasPort consultaListas;
    private final CalculoScorePort calculoScore;
    private final CacheFaixas cacheFaixas;
    private final RegistradorDecisao registradorDecisao;

    public AnalisarRiscoUseCase(ConsultaListasPort consultaListas, CalculoScorePort calculoScore,
                               CacheFaixas cacheFaixas, RegistradorDecisao registradorDecisao) {
        this.consultaListas = consultaListas;
        this.calculoScore = calculoScore;
        this.cacheFaixas = cacheFaixas;
        this.registradorDecisao = registradorDecisao;
    }

    public ResultadoAnalise executar(Transacao transacao) {
        ResultadoListas resultadoListas = consultaListas.consultar(transacao);
        if (resultadoListas.degradado()) {
            LOGGER.warn("Analise prosseguindo sem sinal de listas: a transacao sera avaliada apenas "
                    + "pelas regras que nao dependem de listas");
        }

        CalculoScorePort.ResultadoCalculo calculo = calculoScore.calcular(transacao, resultadoListas);

        TabelaFaixas tabela = cacheFaixas.tabela()
                .orElseThrow(() -> new AnaliseIndisponivelException(
                        "Tabela de faixas de score indisponivel", null));

        FaixaScore faixa = tabela.classificar(calculo.score());

        ResultadoAnalise resultado = new ResultadoAnalise(
                faixa.decisao(), calculo.score(), faixa.classificacao().name(),
                calculo.regrasAcionadas(), resultadoListas);

        registradorDecisao.registrar(transacao, resultado);

        return resultado;
    }

    /**
     * Resultado completo da analise. Somente {@link #decisao()} chega ao cliente; os demais campos
     * alimentam a trilha de auditoria.
     */
    public record ResultadoAnalise(
            Decisao decisao,
            int score,
            String classificacao,
            List<CalculoScorePort.RegraAcionada> regrasAcionadas,
            ResultadoListas resultadoListas) {
    }

    /**
     * Porta de saida para a trilha. Mantida como interface para que o caso de uso permaneca
     * testavel sem broker.
     */
    public interface RegistradorDecisao {
        void registrar(Transacao transacao, ResultadoAnalise resultado);
    }
}
