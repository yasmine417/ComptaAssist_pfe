package com.comptaassist.chat_service.dto;

import com.comptaassist.chat_service.entity.Message;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {

    private UUID id;
    private UUID conversationId;
    private Message.ExpediteurType expediteurType;
    private UUID expediteurId;
    private String expediteurNom;
    private String contenu;
    private boolean lu;
    private LocalDateTime dateLecture;
    private Message.TypeMessage typeMessage;
    private String urlFichier;
    private String nomFichier;
    private LocalDateTime createdAt;

    public static MessageDto fromEntity(Message m) {
        return MessageDto.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .expediteurType(m.getExpediteurType())
                .expediteurId(m.getExpediteurId())
                .expediteurNom(m.getExpediteurNom())
                .contenu(m.getContenu())
                .lu(m.isLu())
                .dateLecture(m.getDateLecture())
                .typeMessage(m.getTypeMessage())
                .urlFichier(m.getUrlFichier())
                .nomFichier(m.getNomFichier())
                .createdAt(m.getCreatedAt())
                .build();
    }
}