package com.agentefinanciero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgenteFinancieroApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenteFinancieroApplication.class, args);
    }
}
