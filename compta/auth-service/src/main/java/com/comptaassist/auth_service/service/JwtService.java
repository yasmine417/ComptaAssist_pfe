package com.comptaassist.auth_service.service;


import com.comptaassist.auth_service.config.JwtConfig;

import com.comptaassist.auth_service.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId",    user.getId().toString());
        claims.put("cabinetId", user.getCabinetId() != null
                ? user.getCabinetId().toString() : null);
        return buildToken(claims, user.getEmail(),
                jwtConfig.getAccessTokenExpiration());
    }

    public String generateRefreshToken(User user) {
        return buildToken(new HashMap<>(), user.getEmail(),
                jwtConfig.getRefreshTokenExpiration());
    }

    private String buildToken(Map<String, Object> claims,
                              String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtConfig.getSecret()));
    }
}