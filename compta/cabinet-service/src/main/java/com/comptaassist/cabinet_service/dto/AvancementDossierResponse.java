package com.comptaassist.cabinet_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvancementDossierResponse {
    private UUID    clientId;
    private String  nomEntreprise;
    private String  comptableNom;     // ← ajouté
    private String  statutCalcule;    // TERMINÉ, EN COURS, EN RETARD
    private long    facturesEnAttente;
    private long    facturesTraitees;
    private String  statutTva;        // DECLAREE, EN_RETARD, A_VENIR, AUCUNE_OBLIGATION
}