package com.comptaassist.tva_service.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Data @NoArgsConstructor @AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FactureDto {
    private UUID      id;
    private UUID      clientId;
    private String    typeOperation;
    private LocalDate dateFacture;
    private Double    montantHt;
    private Double    montantTva;
    private Double    tvaTaux;
    private String    statut;
}