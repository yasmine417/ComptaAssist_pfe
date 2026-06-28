// DocumentUploadResponse.java
package com.comptaassist.document_service.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data @Builder
public class DocumentUploadResponse {
    private UUID   id;
    private String message;
    private String nomOriginal;
    private String typeDocument;
    private Long   taille;
    private String minioObject;
}