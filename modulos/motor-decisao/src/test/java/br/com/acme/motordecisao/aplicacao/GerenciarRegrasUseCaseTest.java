package br.com.acme.motordecisao.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.Regra;
import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraEntity;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraJpaRepository;
import br.com.acme.motordecisao.infraestrutura.persistencia.RegraMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GerenciarRegrasUseCaseTest {

    @Mock
    private RegraJpaRepository repositorio;

    @Mock
    private CacheRegras cacheRegras;

    @Mock
    private GerenciarRegrasUseCase.NotificadorAlteracaoRegras notificador;

    private GerenciarRegrasUseCase gerenciarRegras;
    private final RegraMapper mapper = new RegraMapper();

    private static final UUID ID = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");

    @BeforeEach
    void preparar() {
        gerenciarRegras = new GerenciarRegrasUseCase(repositorio, mapper, cacheRegras, notificador);
    }

    private static Regra regraDeEscada() {
        return Regra.deEscada(ID, "faixa_valor_1", "CARTAO", "cartao ate 300",
                "VALOR_TRANSACAO", new BigDecimal("300.00"), Acao.somar(350));
    }

    private static Regra regraCondicional() {
        return Regra.condicional(ID, "cpf_lista_restritiva", "PIX", "cpf restritivo", null,
                List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(400));
    }

    @Test
    @DisplayName("cria a regra e notifica a alteracao")
    void criaENotifica() {
        Regra regra = regraDeEscada();
        when(repositorio.findByTipoTransacaoAndChave("CARTAO", "faixa_valor_1"))
                .thenReturn(Optional.empty());
        when(repositorio.save(any(RegraEntity.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Regra criada = gerenciarRegras.criar(regra);

        assertThat(criada.chave()).isEqualTo("faixa_valor_1");
        verify(notificador).notificar("CARTAO", GerenciarRegrasUseCase.OperacaoRegra.CRIACAO);
    }

    @Test
    @DisplayName("rejeita chave duplicada no mesmo tipo de transacao")
    void rejeitaChaveDuplicada() {
        Regra regra = regraDeEscada();
        when(repositorio.findByTipoTransacaoAndChave("CARTAO", "faixa_valor_1"))
                .thenReturn(Optional.of(mapper.paraEntidade(
                        Regra.deEscada(UUID.randomUUID(), "faixa_valor_1", "CARTAO", "outra",
                                "VALOR_TRANSACAO", new BigDecimal("300.00"), Acao.somar(200)))));

        assertThatThrownBy(() -> gerenciarRegras.criar(regra))
                .isInstanceOf(ChaveDuplicadaException.class)
                .hasMessageContaining("faixa_valor_1")
                .hasMessageContaining("CARTAO");

        verify(repositorio, never()).save(any(RegraEntity.class));
        verify(notificador, never()).notificar(any(), any());
    }

    @Test
    @DisplayName("regra sem chave nao entra na verificacao de duplicidade")
    void regraSemChaveNaoVerificaDuplicidade() {
        Regra semChave = Regra.condicional(ID, null, "PIX", "aditiva", null,
                List.of(Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA)), Acao.somar(50));
        when(repositorio.save(any(RegraEntity.class))).thenAnswer(chamada -> chamada.getArgument(0));

        gerenciarRegras.criar(semChave);

        verify(repositorio, never()).findByTipoTransacaoAndChave(any(), any());
        verify(notificador).notificar("PIX", GerenciarRegrasUseCase.OperacaoRegra.CRIACAO);
    }

    @Test
    @DisplayName("atualiza a regra existente e notifica")
    void atualizaENotifica() {
        RegraEntity existente = mapper.paraEntidade(regraCondicional());
        when(repositorio.findById(ID)).thenReturn(Optional.of(existente));
        when(repositorio.findByTipoTransacaoAndChave(any(), any())).thenReturn(Optional.of(existente));
        when(repositorio.save(any(RegraEntity.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Regra alterada = Regra.condicional(ID, "cpf_lista_restritiva", "PIX", "nova descricao", null,
                List.of(Condicao.verdadeiro(Campo.CPF_EM_LISTA_RESTRITIVA)), Acao.somar(999));

        Regra resultado = gerenciarRegras.atualizar(ID, alterada);

        assertThat(resultado.acao().pontos()).isEqualTo(999);
        verify(notificador).notificar("PIX", GerenciarRegrasUseCase.OperacaoRegra.ALTERACAO);
    }

    @Test
    @DisplayName("atualizar a propria regra nao acusa chave duplicada")
    void atualizarNaoAcusaDuplicidadeDeSiMesma() {
        RegraEntity existente = mapper.paraEntidade(regraCondicional());
        when(repositorio.findById(ID)).thenReturn(Optional.of(existente));
        when(repositorio.findByTipoTransacaoAndChave("PIX", "cpf_lista_restritiva"))
                .thenReturn(Optional.of(existente));
        when(repositorio.save(any(RegraEntity.class))).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(gerenciarRegras.atualizar(ID, regraCondicional())).isNotNull();
    }

    @Test
    @DisplayName("remove a regra e notifica com o tipo afetado")
    void removeENotifica() {
        RegraEntity existente = mapper.paraEntidade(regraCondicional());
        when(repositorio.findById(ID)).thenReturn(Optional.of(existente));

        gerenciarRegras.remover(ID);

        verify(repositorio).delete(existente);
        verify(notificador).notificar("PIX", GerenciarRegrasUseCase.OperacaoRegra.EXCLUSAO);
    }

    @Test
    @DisplayName("operacoes sobre regra inexistente falham com 404 de dominio")
    void regraInexistente() {
        when(repositorio.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gerenciarRegras.buscar(ID))
                .isInstanceOf(RegraNaoEncontradaException.class);
        assertThatThrownBy(() -> gerenciarRegras.remover(ID))
                .isInstanceOf(RegraNaoEncontradaException.class);
        assertThatThrownBy(() -> gerenciarRegras.atualizar(ID, regraCondicional()))
                .isInstanceOf(RegraNaoEncontradaException.class);
    }

    @Test
    @DisplayName("busca devolve a regra convertida para dominio")
    void buscaRegra() {
        when(repositorio.findById(ID)).thenReturn(Optional.of(mapper.paraEntidade(regraCondicional())));

        assertThat(gerenciarRegras.buscar(ID).chave()).isEqualTo("cpf_lista_restritiva");
    }

    @Test
    @DisplayName("lista todas as regras quando nenhum tipo e informado")
    void listaTodas() {
        when(repositorio.findByAtivaTrue())
                .thenReturn(List.of(mapper.paraEntidade(regraCondicional())));

        assertThat(gerenciarRegras.listarCadastradas(null)).hasSize(1);
        verify(repositorio).findByAtivaTrue();
    }

    @Test
    @DisplayName("lista por tipo normaliza o filtro para maiusculas")
    void listaPorTipoNormalizado() {
        when(repositorio.findByTipoTransacaoAndAtivaTrue("PIX"))
                .thenReturn(List.of(mapper.paraEntidade(regraCondicional())));

        assertThat(gerenciarRegras.listarCadastradas(" pix ")).hasSize(1);
        verify(repositorio).findByTipoTransacaoAndAtivaTrue(eq("PIX"));
    }

    @Test
    @DisplayName("conjunto efetivo delega ao cache")
    void conjuntoEfetivoDelegaAoCache() {
        when(cacheRegras.conjuntoEfetivoDe("PIX")).thenReturn(Optional.empty());

        assertThat(gerenciarRegras.conjuntoEfetivo("PIX")).isEmpty();
        verify(cacheRegras).conjuntoEfetivoDe("PIX");
    }
}
