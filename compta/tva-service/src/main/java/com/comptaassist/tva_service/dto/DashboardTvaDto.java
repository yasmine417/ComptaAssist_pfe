package com.comptaassist.tva_service.dto;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DashboardTvaDto {
    private int clientsActifs;
    private int declareesCeMois;
    private int enAttente;
    private int enRetard;
}