package com.comptaassist.chat_service.dto;

import com.comptaassist.chat_service.entity.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequest {

    @NotNull
    private UUID conversationId;

    @NotBlank
    private String contenu;

    @Builder.Default
    private Message.TypeMessage typeMessage =
            Message.TypeMessage.TEXTE;

    private String urlFichier;
    private String nomFichier;
    private String expediteurId;
}