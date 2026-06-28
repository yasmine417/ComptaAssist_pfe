package com.comptaassist.tva_service.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.UUID;
@Data @NoArgsConstructor @AllArgsConstructor
public class ConfirmerPeriodeRequest {
    private UUID      clientId;
    private UUID      cabinetId;
    // Le comptable peut ajuster les dates si nécessaire
    // (par défaut = dates proposées automatiquement)
    private LocalDate dateDebut;
    private LocalDate dateFin;
}