package com.comptaassist.cabinet_service.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class LiveKitTokenService {

    @Value("${livekit.api-key:devkey}")
    private String apiKey;

    @Value("${livekit.api-secret:secret}")
    private String apiSecret;

    public String genererToken(
            String roomName,
            String identity,
            String nomAffiche) {

        Key signingKey = Keys.hmacShaKeyFor(
                apiSecret.getBytes(StandardCharsets.UTF_8));

        Date now = new Date();
        Date expiration = new Date(now.getTime() + 3600_000);

        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("room", roomName);
        videoGrant.put("roomJoin", true);
        videoGrant.put("canPublish", true);
        videoGrant.put("canSubscribe", true);
        videoGrant.put("canPublishData", true);

        Map<String, Object> claims = new HashMap<>();
        claims.put("video", videoGrant);
        claims.put("name", nomAffiche);

        return Jwts.builder()
                .setIssuer(apiKey)
                .setSubject(identity)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .addClaims(claims)
                .signWith(signingKey)
                .compact();
    }
}