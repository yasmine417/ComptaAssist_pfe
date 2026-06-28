// QuestionRequest.java
package com.comptaassist.fiscal_rag_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuestionRequest {

    @NotBlank(message = "La question est obligatoire")
    private String question;
}