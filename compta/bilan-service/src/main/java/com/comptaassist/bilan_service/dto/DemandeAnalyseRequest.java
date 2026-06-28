// DemandeAnalyseRequest.java
package com.comptaassist.bilan_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class DemandeAnalyseRequest {
    @NotNull
    private UUID documentId;
    @NotNull
    private UUID clientId;
    @NotNull
    private UUID cabinetId;
    @NotNull
    private Integer exercice;
    // Chemin MinIO du PDF
    @NotNull
    private String cheminPdf;
}