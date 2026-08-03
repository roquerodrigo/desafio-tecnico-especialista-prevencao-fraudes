package br.com.acme.motordecisao.infraestrutura.config;

import br.com.acme.motordecisao.infraestrutura.cache.CacheRegras;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CargaInicialCacheRegras {

    private static final Logger LOGGER = LoggerFactory.getLogger(CargaInicialCacheRegras.class);

    private final CacheRegras cacheRegras;

    public CargaInicialCacheRegras(CacheRegras cacheRegras) {
        this.cacheRegras = cacheRegras;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void carregar() {
        if (!cacheRegras.recarregar()) {
            LOGGER.error("Carga inicial do cache de regras falhou. O servico permanece com readiness "
                    + "DOWN ate uma recarga bem-sucedida — atender sem regras produziria score arbitrario.");
        }
    }
}
