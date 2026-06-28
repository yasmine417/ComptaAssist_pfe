package com.comptaassist.auth_service.dto;

// AuthResponse.java


import lombok.Builder;
import lombok.Data;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String type = "Bearer";
    private UserResponse user;
}
