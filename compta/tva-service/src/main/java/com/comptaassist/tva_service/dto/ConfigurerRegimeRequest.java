package com.comptaassist.tva_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurerRegimeRequest {
    private UUID clientId;
    private UUID   cabinetId;
    private String regime; // "MENSUEL" | "TRIMESTRIEL"
}