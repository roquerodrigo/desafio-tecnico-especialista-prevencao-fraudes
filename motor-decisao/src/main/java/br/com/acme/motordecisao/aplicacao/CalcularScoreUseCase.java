package br.com.acme.motordecisao.aplicacao;

import br.com.acme.motordecisao.dominio.CalculadoraScore;
import br.com.acme.motordecisao.dominio.ResultadoScore;
import br.com.acme.motordecisao.dominio.avaliacao.ContextoAvaliacao;
import br.com.acme.motordecisao.dominio.composicao.ConjuntoEfetivo;
import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import org.springframework.stereotype.Service;

@Service
public class CalcularScoreUseCase {

    private final CacheRegras cacheRegras;

    public CalcularScoreUseCase(CacheRegras cacheRegras) {
        this.cacheRegras = cacheRegras;
    }

    public ResultadoScore executar(ContextoAvaliacao contexto) {
        ConjuntoEfetivo conjunto = cacheRegras.conjuntoEfetivoDe(contexto.tipoTransacao())
                .orElseThrow(() -> new ConjuntoRegrasIndisponivelException(
                        "Nenhum conjunto de regras disponivel para o tipo " + contexto.tipoTransacao()));

        return CalculadoraScore.calcular(conjunto, contexto);
    }
}
