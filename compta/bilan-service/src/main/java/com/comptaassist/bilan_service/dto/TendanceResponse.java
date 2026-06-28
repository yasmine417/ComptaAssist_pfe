// TendanceResponse.java
package com.comptaassist.bilan_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class TendanceResponse {
    private UUID   id;
    private UUID   clientId;
    private String indicateur;
    private Double valeurActuelle;
    private Double valeurPrecedente;
    private String typeAlerte;
    private String message;
    private boolean estTraite;
    private LocalDateTime dateDetection;
}