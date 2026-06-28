package com.comptaassist.auth_service.dto;

// RegisterRequest.java


import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {
    @NotBlank
    private String nom;
    @NotBlank
    private String prenom;
    @Email @NotBlank
    private String email;
    @NotBlank @Size(min = 8)
    private String password;
    private String role;
    private UUID cabinetId;
    private String statut; // optionnel// optionnel, défaut COMPTABLE
}