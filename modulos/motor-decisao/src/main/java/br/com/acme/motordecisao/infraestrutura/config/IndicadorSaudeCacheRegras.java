package br.com.acme.motordecisao.infraestrutura.config;

import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Mantem o servico fora do balanceador enquanto nao houver cache de regras valido. Atender sem
 * regras produziria score arbitrario, que a orquestracao interpretaria como decisao legitima.
 */
@Component("regras")
public class IndicadorSaudeCacheRegras implements HealthIndicator {

    private final CacheRegras cacheRegras;

    public IndicadorSaudeCacheRegras(CacheRegras cacheRegras) {
        this.cacheRegras = cacheRegras;
    }

    @Override
    public Health health() {
        if (cacheRegras.carregado()) {
            return Health.up().withDetail("cache", "carregado").build();
        }
        return Health.down()
                .withDetail("cache", "vazio")
                .withDetail("motivo", "nenhum conjunto de regras carregado")
                .build();
    }
}
