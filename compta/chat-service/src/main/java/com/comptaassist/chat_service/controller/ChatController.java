package com.comptaassist.chat_service.controller;

import com.comptaassist.chat_service.config.JwtUtil;
import com.comptaassist.chat_service.dto.MessageRequest;
import com.comptaassist.chat_service.entity.Message;
import com.comptaassist.chat_service.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final JwtUtil jwtUtil;

    @MessageMapping("/chat.envoyer")
    public void envoyerMessage(
            @Payload MessageRequest request,
            Principal principal) {

        if (principal == null) {
            log.error("Principal null — message ignoré");
            return;
        }

        try {
            UUID expediteurId = UUID.fromString(
                    principal.getName());

            // Le type et le nom seront déduits
            // depuis la conversation dans ChatService
            chatService.envoyerMessageAuto(
                    request.getConversationId(),
                    expediteurId,
                    request.getContenu(),
                    request.getTypeMessage() != null
                            ? request.getTypeMessage()
                            : Message.TypeMessage.TEXTE,
                    request.getUrlFichier(),
                    request.getNomFichier());

        } catch (Exception e) {
            log.error("Erreur envoi message WS: {}",
                    e.getMessage(), e);
        }
    }

    @MessageMapping("/chat.lire/{conversationId}")
    public void marquerLus(
            @DestinationVariable UUID conversationId,
            @Header(value = "Authorization",
                    required = false) String token,
            Principal principal) {

        try {
            Message.ExpediteurType lecteurType;

            if (token != null
                    && token.startsWith("Bearer ")) {
                String jwt = token.substring(7);
                String role = jwtUtil.extractRole(jwt);
                lecteurType = role != null
                        && role.contains("COMPTABLE")
                        ? Message.ExpediteurType.COMPTABLE
                        : Message.ExpediteurType.CLIENT;
            } else {
                // Pas de JWT → c'est le client
                lecteurType =
                        Message.ExpediteurType.CLIENT;
            }

            chatService.marquerLus(
                    conversationId, lecteurType);

        } catch (Exception e) {
            log.error("Erreur marquage lus: {}",
                    e.getMessage());
        }
    }
}