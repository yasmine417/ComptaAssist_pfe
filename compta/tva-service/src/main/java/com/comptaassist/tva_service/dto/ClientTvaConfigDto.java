package com.comptaassist.tva_service.dto;

import lombok.*;
import java.util.UUID;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClientTvaConfigDto {
    private UUID   clientId;
    private String regime; // MENSUEL | TRIMESTRIEL
}