package com.comptaassist.fiscal_rag_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class FiscalRagServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FiscalRagServiceApplication.class, args);
	}

}
