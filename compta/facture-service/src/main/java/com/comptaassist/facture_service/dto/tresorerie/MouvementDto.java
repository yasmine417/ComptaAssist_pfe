package com.comptaassist.facture_service.dto.tresorerie;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
@Data @Builder
public class MouvementDto {
    private String id;
    private String date;
    private String tiers;
    private String numeroFacture;
    private double montant;
    private String type;       // ENCAISSEMENT | DECAISSEMENT
    private String mode;       // VIREMENT | CHEQUE | ESPECES
    private String reference;
}