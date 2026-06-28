package com.comptaassist.cabinet_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class MembreResponse {
    private UUID          id;
    private UUID          cabinetId;
    private UUID          userId;
    private String        nom;
    private String        prenom;
    private String        email;
    private String        role;
    private boolean       actif;
    private LocalDateTime createdAt;
}