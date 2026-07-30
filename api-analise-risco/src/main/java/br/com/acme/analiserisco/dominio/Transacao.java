package br.com.acme.analiserisco.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dados da transacao a avaliar. Validado na construcao — nao existe {@code Transacao} invalida.
 *
 * <p>{@code valorTransacao} e {@link BigDecimal} com no maximo duas casas decimais. Centavo e a
 * menor unidade do real; aceitar fracao de centavo introduziria arredondamento silencioso
 * exatamente na comparacao com os limites de faixa, que e onde o sistema decide a pontuacao.
 */
public record Transacao(
        String cpf,
        String ip,
        UUID idDispositivo,
        String tipoTransacao,
        BigDecimal valorTransacao) {

    private static final int MAXIMO_CASAS_DECIMAIS = 2;

    public Transacao {
        List<String> problemas = new ArrayList<>();

        if (!ValidadorCpf.valido(cpf)) {
            problemas.add("cpf: CPF invalido");
        }
        if (ip == null || ip.isBlank()) {
            problemas.add("ip: IP e obrigatorio");
        }
        if (idDispositivo == null) {
            problemas.add("idDispositivo: identificador do dispositivo e obrigatorio");
        }
        if (tipoTransacao == null || tipoTransacao.isBlank()) {
            problemas.add("tipoTransacao: tipo de transacao e obrigatorio");
        }
        if (valorTransacao == null) {
            problemas.add("valorTransacao: valor e obrigatorio");
        } else {
            if (valorTransacao.signum() <= 0) {
                problemas.add("valorTransacao: valor deve ser maior que zero");
            }
            if (valorTransacao.scale() > MAXIMO_CASAS_DECIMAIS) {
                problemas.add("valorTransacao: valor admite no maximo 2 casas decimais");
            }
        }

        if (!problemas.isEmpty()) {
            throw new TransacaoInvalidaException(problemas);
        }

        tipoTransacao = tipoTransacao.trim().toUpperCase();
    }
}
