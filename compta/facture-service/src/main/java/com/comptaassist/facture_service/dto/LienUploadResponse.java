package com.comptaassist.facture_service.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data @Builder
public class LienUploadResponse {
    private String        token;
    private String        url;
    private LocalDateTime expiresAt;
    private String        nomClient;
    private String        emailClient;
    private Boolean       actif;
    private Integer       nbFichiersUploades;
}