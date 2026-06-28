package com.comptaassist.cabinet_service.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class RapportMensuelRequest {
    private UUID clientId;
    private UUID cabinetId;
    private UUID comptableId;
    private String nomEntreprise;
    private String moisLabel;
    private String contenuHtml;
}