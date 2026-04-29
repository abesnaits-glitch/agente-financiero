package com.agentefinanciero.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/checkout").setViewName("forward:/checkout.html");
        registry.addViewController("/checkout/success").setViewName("forward:/checkout.html");
    }
}
