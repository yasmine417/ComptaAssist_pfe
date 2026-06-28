package com.comptaassist.bilan_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class BilanServiceApplication {
	public static void main(String[] args) {
		SpringApplication.run(
				BilanServiceApplication.class, args);
	}
}