package br.com.acme.motordecisao.dominio.composicao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.acme.motordecisao.dominio.regra.Acao;
import br.com.acme.motordecisao.dominio.regra.Campo;
import br.com.acme.motordecisao.dominio.regra.Condicao;
import br.com.acme.motordecisao.dominio.regra.Regra;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Um teste nomeado por cenario de composicao do enunciado (SC-012).
 */
class ConjuntoEfetivoTest {

    private static final String VALOR = "VALOR_TRANSACAO";

    private static Regra faixa(String chave, String tipo, String limite, int pontos) {
        return Regra.deEscada(UUID.randomUUID(), chave, tipo, chave + "@" + tipo, VALOR,
                limite == null ? null : new BigDecimal(limite), Acao.somar(pontos));
    }

    private static Regra condicional(String chave, String tipo, Campo campo, Acao acao) {
        return Regra.condicional(UUID.randomUUID(), chave, tipo, chave + "@" + tipo,
                null, List.of(Condicao.verdadeiro(campo)), acao);
    }

    private static List<Regra> conjuntoPadrao() {
        return List.of(
                faixa("faixa_valor_1", "PADRAO", "300.00", 200),
                faixa("faixa_valor_2", "PADRAO", "5000.00", 300),
                faixa("faixa_valor_3", "PADRAO", "20000.00", 400),
                faixa("faixa_valor_4", "PADRAO", null, 500),
                condicional("cpf_lista_permissiva", "PADRAO", Campo.CPF_EM_LISTA_PERMISSIVA, Acao.subtrair(200)),
                condicional("cpf_lista_restritiva", "PADRAO", Campo.CPF_EM_LISTA_RESTRITIVA, Acao.somar(400)));
    }

    private static Regra regraPorChave(ConjuntoEfetivo conjunto, String chave) {
        return conjunto.todasAsRegras().stream()
                .filter(regra -> chave.equals(regra.chave()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("chave ausente no conjunto efetivo: " + chave));
    }

    @Test
    @DisplayName("regra especifica com a mesma chave substitui a regra padrao")
    void mesmaChaveSubstitui() {
        List<Regra> especificas = List.of(faixa("faixa_valor_1", "CARTAO", "300.00", 350));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("CARTAO", conjuntoPadrao(), especificas);

        assertThat(regraPorChave(efetivo, "faixa_valor_1").acao().pontos()).isEqualTo(350);
        assertThat(efetivo.origemDaChave("faixa_valor_1")).isEqualTo("CARTAO");
    }

    @Test
    @DisplayName("regra padrao sem correspondente especifico permanece ativa")
    void padraoSemCorrespondentePermanece() {
        List<Regra> especificas = List.of(faixa("faixa_valor_1", "CARTAO", "300.00", 350));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("CARTAO", conjuntoPadrao(), especificas);

        assertThat(regraPorChave(efetivo, "faixa_valor_2").acao().pontos()).isEqualTo(300);
        assertThat(regraPorChave(efetivo, "faixa_valor_3").acao().pontos()).isEqualTo(400);
        assertThat(regraPorChave(efetivo, "faixa_valor_4").acao().pontos()).isEqualTo(500);
        assertThat(efetivo.origemDaChave("faixa_valor_2")).isEqualTo("PADRAO");
    }

    @Test
    @DisplayName("chave inexistente no padrao e acrescentada ao conjunto efetivo")
    void chaveNovaEhAcrescentada() {
        Regra novaCondicional = condicional("ip_lista_restritiva", "PIX",
                Campo.IP_EM_LISTA_RESTRITIVA, Acao.somar(400));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("PIX", conjuntoPadrao(), List.of(novaCondicional));

        assertThat(regraPorChave(efetivo, "ip_lista_restritiva").acao().pontos()).isEqualTo(400);
        assertThat(efetivo.origemDaChave("ip_lista_restritiva")).isEqualTo("PIX");
        assertThat(efetivo.todasAsRegras()).hasSize(7);
    }

    @Test
    @DisplayName("regra sem chave e sempre aditiva e nunca substitui outra")
    void regraSemChaveEhAditiva() {
        Regra semChaveA = Regra.condicional(UUID.randomUUID(), null, "PIX", "aditiva A",
                null, List.of(Condicao.verdadeiro(Campo.IP_EM_LISTA_RESTRITIVA)), Acao.somar(50));
        Regra semChaveB = Regra.condicional(UUID.randomUUID(), null, "PIX", "aditiva B",
                null, List.of(Condicao.verdadeiro(Campo.DISPOSITIVO_EM_LISTA_RESTRITIVA)), Acao.somar(70));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("PIX", conjuntoPadrao(),
                List.of(semChaveA, semChaveB));

        assertThat(efetivo.todasAsRegras()).hasSize(8);
        assertThat(efetivo.regrasCondicionais())
                .extracting(Regra::descricao)
                .contains("aditiva A", "aditiva B");
    }

    @Test
    @DisplayName("tipo sem regras especificas aplica apenas o conjunto padrao")
    void tipoSemEspecificasUsaSomenteOPadrao() {
        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("BOLETO", conjuntoPadrao(), List.of());

        assertThat(efetivo.todasAsRegras()).hasSize(6);
        assertThat(efetivo.todasAsRegras())
                .allSatisfy(regra -> assertThat(efetivo.origemDaChave(regra.chave())).isEqualTo("PADRAO"));
    }

    @Test
    @DisplayName("conjunto especifico nulo e tratado como vazio")
    void especificasNulasSaoToleradas() {
        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("TED", conjuntoPadrao(), null);

        assertThat(efetivo.todasAsRegras()).hasSize(6);
    }

    @Test
    @DisplayName("cenario CARTAO do enunciado: duas chaves redefinidas, resto herdado")
    void cenarioCartaoDoEnunciado() {
        List<Regra> cartao = List.of(
                faixa("faixa_valor_1", "CARTAO", "300.00", 350),
                condicional("cpf_lista_permissiva", "CARTAO", Campo.CPF_EM_LISTA_PERMISSIVA, Acao.subtrair(400)));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("CARTAO", conjuntoPadrao(), cartao);

        assertThat(regraPorChave(efetivo, "faixa_valor_1").acao().pontos()).isEqualTo(350);
        assertThat(regraPorChave(efetivo, "cpf_lista_permissiva").acao().pontos()).isEqualTo(400);
        assertThat(regraPorChave(efetivo, "faixa_valor_2").acao().pontos()).isEqualTo(300);
        assertThat(regraPorChave(efetivo, "cpf_lista_restritiva").acao().pontos()).isEqualTo(400);
        assertThat(efetivo.origemDaChave("faixa_valor_1")).isEqualTo("CARTAO");
        assertThat(efetivo.origemDaChave("faixa_valor_2")).isEqualTo("PADRAO");
    }

    @Test
    @DisplayName("cenario TED do enunciado: primeira e ultima faixa redefinidas")
    void cenarioTedDoEnunciado() {
        List<Regra> ted = List.of(
                faixa("faixa_valor_1", "TED", "300.00", 280),
                faixa("faixa_valor_4", "TED", null, 750));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("TED", conjuntoPadrao(), ted);

        assertThat(regraPorChave(efetivo, "faixa_valor_1").acao().pontos()).isEqualTo(280);
        assertThat(regraPorChave(efetivo, "faixa_valor_4").acao().pontos()).isEqualTo(750);
        assertThat(regraPorChave(efetivo, "faixa_valor_2").acao().pontos()).isEqualTo(300);
        assertThat(regraPorChave(efetivo, "faixa_valor_3").acao().pontos()).isEqualTo(400);
    }

    @Test
    @DisplayName("redefinir faixa com outro teto nao cria lacuna nem sobreposicao")
    void redefinirTetoMantemEscadaValida() {
        List<Regra> especificas = List.of(faixa("faixa_valor_1", "CARTAO", "500.00", 350));

        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("CARTAO", conjuntoPadrao(), especificas);

        assertThat(efetivo.escadas()).hasSize(1);
        assertThat(efetivo.escadas().iterator().next().resolver(new BigDecimal("400.00")).chave())
                .isEqualTo("faixa_valor_1");
        assertThat(efetivo.escadas().iterator().next().resolver(new BigDecimal("600.00")).chave())
                .isEqualTo("faixa_valor_2");
    }

    @Test
    @DisplayName("tipo de transacao e normalizado para maiusculas")
    void tipoEhNormalizado() {
        ConjuntoEfetivo efetivo = ConjuntoEfetivo.compor("pix", conjuntoPadrao(), List.of());

        assertThat(efetivo.tipoTransacao()).isEqualTo("PIX");
    }
}
