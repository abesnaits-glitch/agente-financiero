package com.agentefinanciero.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    @DependsOn("entityManagerFactory")
    public Flyway flyway(DataSource dataSource) {
        log.info("[Flyway] Iniciando migracion usando DataSource de Spring");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .baselineDescription("Pre-Flyway production state")
                .load();
        flyway.migrate();
        return flyway;
    }
}
