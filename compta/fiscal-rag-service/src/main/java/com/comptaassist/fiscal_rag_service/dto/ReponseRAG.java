// ReponseRAG.java
package com.comptaassist.fiscal_rag_service.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class ReponseRAG {
    private String       reponse;
    private List<String> sources;
    private Integer      nbExtraits;
    private List<String> extraits;
}