package com.comptaassist.chat_service.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret));
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return extractAllClaims(token)
                .get("userId", String.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token)
                .get("role", String.class);
    }

    public String extractEmail(String token) {
        return extractAllClaims(token)
                .get("email", String.class);
    }

    public String extractNom(String token) {
        String prenom = extractAllClaims(token)
                .get("prenom", String.class);
        String nom = extractAllClaims(token)
                .get("nom", String.class);
        return ((prenom != null ? prenom : "") + " " +
                (nom != null ? nom : "")).trim();
    }
}