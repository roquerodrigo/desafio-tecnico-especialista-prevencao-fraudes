package br.com.acme.analiserisco.infraestrutura.config;

import br.com.acme.analiserisco.infraestrutura.cache.CacheFaixas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CargaInicialCacheFaixas {

    private static final Logger LOGGER = LoggerFactory.getLogger(CargaInicialCacheFaixas.class);

    private final CacheFaixas cacheFaixas;

    public CargaInicialCacheFaixas(CacheFaixas cacheFaixas) {
        this.cacheFaixas = cacheFaixas;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void carregar() {
        if (!cacheFaixas.recarregar()) {
            LOGGER.error("Carga inicial das faixas de score falhou. O readiness permanece DOWN: "
                    + "classificar score sem tabela de faixas produziria decisao arbitraria.");
        }
    }
}
