package com.comptaassist.auth_service.dto;

// UserResponse.java


import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class UserResponse {
    private UUID id;
    private String nom;
    private String prenom;
    private String email;
    private String role;
    private UUID cabinetId;
    private boolean actif;
    private String  statut;

}
