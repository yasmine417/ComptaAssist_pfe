package com.comptaassist.fiscal_rag_service.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentIndexeDto {
    private UUID          id;
    private String        nomDocument;
    private String        nomFichier;
    private Long          tailleFichier;
    private String        statut;
    private String        message;
    private Integer       nbMorceaux;
    private String        adminEmail;
    private LocalDateTime indexeA;
    private LocalDateTime reindexeA;
}