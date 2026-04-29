package com.agentefinanciero.config;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MercadoPagoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoConfiguration.class);

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);

        boolean presente = accessToken != null && !accessToken.isBlank();
        if (presente) {
            String preview = accessToken.length() >= 10
                    ? accessToken.substring(0, 10) + "..."
                    : accessToken;
            log.info("[MP] Token configurado: SI (primeros 10 chars: {})", preview);
        } else {
            log.error("[MP] Token configurado: NO - ES NULL o vacío");
        }
    }
}
