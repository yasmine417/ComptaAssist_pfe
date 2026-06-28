package com.comptaassist.cabinet_service.dto;

// CabinetResponse.java


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class CabinetResponse {
    private UUID id;
    private String nom;
    private String email;
    private String telephone;
    private String adresse;
    private UUID directeurId;
    private boolean actif;
    private LocalDateTime createdAt;
}