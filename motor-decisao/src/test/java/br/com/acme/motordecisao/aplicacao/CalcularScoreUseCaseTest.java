package br.com.acme.motordecisao.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.acme.motordecisao.dominio.ResultadoScore;
import br.com.acme.motordecisao.dominio.avaliacao.ContextoAvaliacao;
import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalcularScoreUseCaseTest {

    @Mock
    private CacheRegras cacheRegras;

    private static final ContextoAvaliacao CONTEXTO = new ContextoAvaliacao(
            "PIX", new BigDecimal("1500.00"), false, false, false, false);

    @Test
    @DisplayName("calcula o score a partir do conjunto efetivo do cache")
    void calculaComConjuntoDoCache() {
        List<Regra> regras = List.of(
                Regra.deEscada(UUID.randomUUID(), "faixa_valor_1", "PADRAO", "ate 300",
                        "VALOR_TRANSACAO", new BigDecimal("300.00"), Acao.somar(200)),
                Regra.deEscada(UUID.randomUUID(), "faixa_valor_2", "PADRAO", "acima de 300",
                        "VALOR_TRANSACAO", null, Acao.somar(300)));
        when(cacheRegras.conjuntoEfetivoDe("PIX"))
                .thenReturn(Optional.of(ConjuntoEfetivo.compor("PIX", regras, List.of())));

        ResultadoScore resultado = new CalcularScoreUseCase(cacheRegras).executar(CONTEXTO);

        assertThat(resultado.score()).isEqualTo(300);
    }

    @Test
    @DisplayName("sem conjunto de regras, prefere nao pontuar a pontuar errado")
    void semConjuntoFalha() {
        when(cacheRegras.conjuntoEfetivoDe("PIX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CalcularScoreUseCase(cacheRegras).executar(CONTEXTO))
                .isInstanceOf(ConjuntoRegrasIndisponivelException.class)
                .hasMessageContaining("PIX");
    }

    @Test
    @DisplayName("resultado de score rejeita valor abaixo do piso")
    void resultadoRejeitaScoreInvalido() {
        assertThatThrownBy(() -> new ResultadoScore(0, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no minimo 1");
    }
}
