package com.comptaassist.cabinet_service.dto;

// ClientResponse.java

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class ClientResponse {
    private UUID id;
    private UUID cabinetId;
    private UUID comptableId;
    private String nomEntreprise;
    private String numeroFiscal;
    private String ice;
    private String email;
    private String telephone;
    private String adresse;
    private String secteur;
    private boolean actif;
    private LocalDateTime createdAt;
    private java.math.BigDecimal capitalSocial;
}