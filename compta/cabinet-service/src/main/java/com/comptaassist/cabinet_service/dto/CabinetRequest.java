package com.comptaassist.cabinet_service.dto;

// CabinetRequest.java

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CabinetRequest {
    @NotBlank
    private String nom;
    @Email @NotBlank
    private String email;
    private String telephone;
    private String adresse;
}