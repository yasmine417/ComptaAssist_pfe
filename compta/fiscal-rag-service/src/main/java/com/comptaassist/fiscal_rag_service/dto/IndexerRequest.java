// IndexerRequest.java
package com.comptaassist.fiscal_rag_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IndexerRequest {

    @NotBlank
    private String cheminPdf;

    @NotBlank
    private String nomDocument;
}