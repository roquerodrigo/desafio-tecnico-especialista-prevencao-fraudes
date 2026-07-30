package br.com.acme.analiserisco.infraestrutura.config;

import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("faixasScore")
public class IndicadorSaudeCacheFaixas implements HealthIndicator {

    private final CacheFaixas cacheFaixas;

    public IndicadorSaudeCacheFaixas(CacheFaixas cacheFaixas) {
        this.cacheFaixas = cacheFaixas;
    }

    @Override
    public Health health() {
        if (cacheFaixas.carregado()) {
            return Health.up().withDetail("faixas", "carregadas").build();
        }
        return Health.down()
                .withDetail("faixas", "nao carregadas")
                .withDetail("motivo", "classificar sem tabela de faixas produziria decisao arbitraria")
                .build();
    }
}
