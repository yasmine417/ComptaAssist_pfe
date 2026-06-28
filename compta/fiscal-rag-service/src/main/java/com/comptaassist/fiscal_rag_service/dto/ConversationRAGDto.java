package com.comptaassist.fiscal_rag_service.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ConversationRAGDto {
    private UUID          id;
    private String        question;
    private String        reponse;
    private List<String>  sources;
    private Integer       nbExtraits;
    private LocalDateTime poseeA;
}