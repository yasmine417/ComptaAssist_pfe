package com.comptaassist.auth_service.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter @Setter
public class JwtConfig {
    private String secret;
    private long accessTokenExpiration;   // en ms, ex: 900000 (15 min)
    private long refreshTokenExpiration;  // en ms, ex: 604800000 (7 jours)
}