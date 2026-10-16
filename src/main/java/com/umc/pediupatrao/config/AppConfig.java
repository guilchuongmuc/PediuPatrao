package com.umc.pediupatrao.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Relógio único da aplicação (fuso da pizzaria); injetável para facilitar os testes. */
    @Bean
    public Clock clock(@Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
