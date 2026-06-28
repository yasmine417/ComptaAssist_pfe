package com.comptaassist.auth_service.dto;

// UpdateProfileRequest.java

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String nom;
    private String prenom;
    private String password;
}