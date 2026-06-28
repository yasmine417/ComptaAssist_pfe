// DocumentResponse.java
package com.comptaassist.document_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class DocumentResponse {
    private UUID          id;
    private String        nomOriginal;
    private String        typeDocument;
    private Long          taille;
    private String        contentType;
    private UUID          clientId;
    private UUID          cabinetId;
    private UUID          uploadedBy;
    private boolean       analyse;
    private LocalDateTime createdAt;
    private String        urlTelechargement;
    private String        minioObject;
}