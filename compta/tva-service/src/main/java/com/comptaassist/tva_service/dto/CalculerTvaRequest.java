package com.comptaassist.tva_service.dto;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Data @NoArgsConstructor @AllArgsConstructor
public class CalculerTvaRequest {
    private UUID      clientId;
    private UUID      cabinetId;
    private String    regime;        // "MENSUEL" | "TRIMESTRIEL"
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String    periodeLabel;  // "Jan 2026" ou "T1 2026"
}