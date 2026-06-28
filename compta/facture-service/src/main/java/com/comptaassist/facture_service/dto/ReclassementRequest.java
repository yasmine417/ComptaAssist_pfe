package com.comptaassist.facture_service.dto;

import lombok.Data;

/**
 * Requête envoyée par le comptable quand il confirme
 * la classification immobilisation/charge.
 */
@Data
public class ReclassementRequest {

    /** UUID de la facture à reclasser */
    private String factureId;

    /**
     * Choix du comptable :
     *   "IMMOBILISATION" → garder écriture 2355/34551/4411 (Bilan)
     *   "CHARGE"         → basculer en écriture 6xxx/3455/4411 (CPC)
     */
    private String classification;
}