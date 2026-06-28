package com.comptaassist.cabinet_service.dto;

// ClientRequest.java


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class ClientRequest {
    @NotBlank
    private String nomEntreprise;
    @NotBlank
    private String numeroFiscal;
    private String ice;
    private String email;
    private String telephone;
    private String adresse;
    private String secteur;
    private UUID comptableId;
    private java.math.BigDecimal capitalSocial;
}
