package com.comptaassist.facture_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO minimal pour récupérer l'ICE d'un client
 * depuis cabinet-service via appel REST.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientIceDto {
    private String id;
    private String ice;
    private String nomEntreprise;
}