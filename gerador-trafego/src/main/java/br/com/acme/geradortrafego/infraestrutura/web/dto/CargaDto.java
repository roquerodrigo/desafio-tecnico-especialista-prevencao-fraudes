package br.com.acme.geradortrafego.infraestrutura.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

public final class CargaDto {

    private CargaDto() {
    }

    /**
     * Todos os campos sao opcionais; os valores padrao produzem uma carga curta o suficiente para
     * uma demonstracao e longa o bastante para o p95 ter significado estatistico.
     *
     * @param semente fixa o sorteio, tornando a carga reproduzivel. Zero ou ausente usa aleatoriedade real.
     */
    public record Requisicao(
            @Positive(message = "duracaoSegundos deve ser maior que zero")
            @Max(value = 600, message = "duracaoSegundos deve ser no maximo 600")
            Integer duracaoSegundos,

            @Positive(message = "requisicoesPorSegundo deve ser maior que zero")
            @Max(value = 1000, message = "requisicoesPorSegundo deve ser no maximo 1000")
            Integer requisicoesPorSegundo,

            Long semente) {

        private static final int DURACAO_PADRAO_SEGUNDOS = 30;
        private static final int REQUISICOES_POR_SEGUNDO_PADRAO = 50;

        public int duracaoOuPadrao() {
            return duracaoSegundos == null ? DURACAO_PADRAO_SEGUNDOS : duracaoSegundos;
        }

        public int requisicoesPorSegundoOuPadrao() {
            return requisicoesPorSegundo == null ? REQUISICOES_POR_SEGUNDO_PADRAO : requisicoesPorSegundo;
        }

        public long sementeOuAleatoria() {
            return semente == null ? 0L : semente;
        }
    }
}
