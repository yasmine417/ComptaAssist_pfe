package com.comptaassist.fiscal_rag_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
/*
    @Value("${python.rag.url}")
    private String pythonRagUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl(pythonRagUrl)
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        "application/json; charset=UTF-8")
                .defaultHeader(
                        HttpHeaders.ACCEPT,
                        "application/json")
                .codecs(c -> {
                    c.defaultCodecs()
                            .maxInMemorySize(10 * 1024 * 1024);
                    c.defaultCodecs()
                            .jackson2JsonEncoder(
                                    new Jackson2JsonEncoder(
                                            new com.fasterxml.jackson
                                                    .databind.ObjectMapper(),
                                            MediaType.APPLICATION_JSON));
                })
                .build();
    }*/
}