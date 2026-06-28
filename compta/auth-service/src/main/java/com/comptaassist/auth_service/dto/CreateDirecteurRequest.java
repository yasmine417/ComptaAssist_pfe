// CreateDirecteurRequest.java
package com.comptaassist.auth_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateDirecteurRequest {
    @NotBlank private String nom;
    @NotBlank private String prenom;
    @Email @NotBlank private String email;
}