package com.comptaassist.chat_service.dto;

import com.comptaassist.chat_service.entity.Conversation;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {

    private UUID id;
    private UUID comptableId;
    private String comptableNom;
    private String comptableEmail;
    private UUID clientId;
    private String clientNom;
    private String clientEmail;
    private UUID cabinetId;
    private String dernierMessage;
    private LocalDateTime dateDernierMessage;
    private int nonLusComptable;
    private int nonLusClient;
    private boolean active;
    private LocalDateTime createdAt;

    public static ConversationDto fromEntity(Conversation c) {
        return ConversationDto.builder()
                .id(c.getId())
                .comptableId(c.getComptableId())
                .comptableNom(c.getComptableNom())
                .comptableEmail(c.getComptableEmail())
                .clientId(c.getClientId())
                .clientNom(c.getClientNom())
                .clientEmail(c.getClientEmail())
                .cabinetId(c.getCabinetId())
                .dernierMessage(c.getDernierMessage())
                .dateDernierMessage(c.getDateDernierMessage())
                .nonLusComptable(c.getNonLusComptable())
                .nonLusClient(c.getNonLusClient())
                .active(c.isActive())
                .createdAt(c.getCreatedAt())
                .build();
    }
}