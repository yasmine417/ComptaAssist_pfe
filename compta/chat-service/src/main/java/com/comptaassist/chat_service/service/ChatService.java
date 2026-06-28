package com.comptaassist.chat_service.service;

import com.comptaassist.chat_service.dto.*;
import com.comptaassist.chat_service.entity.*;
import com.comptaassist.chat_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepo;
    private final MessageRepository messageRepo;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaNotificationService kafkaService;

    // ── Créer ou récupérer une conversation ──────────────
    @Transactional
    public ConversationDto ouvrirConversation(
            UUID comptableId, String comptableNom,
            String comptableEmail,
            UUID clientId, String clientNom,
            String clientEmail, UUID cabinetId) {

        Optional<Conversation> existing =
                conversationRepo.findByComptableIdAndClientId(
                        comptableId, clientId);

        if (existing.isPresent()) {
            return ConversationDto.fromEntity(existing.get());
        }

        Conversation conv = Conversation.builder()
                .comptableId(comptableId)
                .comptableNom(comptableNom)
                .comptableEmail(comptableEmail)
                .clientId(clientId)
                .clientNom(clientNom)
                .clientEmail(clientEmail)
                .cabinetId(cabinetId)
                .build();

        return ConversationDto.fromEntity(
                conversationRepo.save(conv));
    }

    // ── Envoyer un message ────────────────────────────────
    @Transactional
    public MessageDto envoyerMessage(
            UUID conversationId,
            UUID expediteurId,
            String expediteurNom,
            Message.ExpediteurType expediteurType,
            String contenu,
            Message.TypeMessage typeMessage,
            String urlFichier,
            String nomFichier) {

        Conversation conv = conversationRepo
                .findById(conversationId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Conversation introuvable"));

        // Créer le message
        Message msg = Message.builder()
                .conversationId(conversationId)
                .expediteurId(expediteurId)
                .expediteurNom(expediteurNom)
                .expediteurType(expediteurType)
                .contenu(contenu)
                .typeMessage(typeMessage)
                .urlFichier(urlFichier)
                .nomFichier(nomFichier)
                .build();

        Message saved = messageRepo.save(msg);

        // Mettre à jour la conversation
        conv.setDernierMessage(
                contenu.length() > 100
                        ? contenu.substring(0, 100) + "..."
                        : contenu);
        conv.setDateDernierMessage(LocalDateTime.now());

        if (expediteurType ==
                Message.ExpediteurType.COMPTABLE) {
            conv.setNonLusClient(
                    conv.getNonLusClient() + 1);
        } else {
            conv.setNonLusComptable(
                    conv.getNonLusComptable() + 1);
        }
        conversationRepo.save(conv);

        MessageDto dto = MessageDto.fromEntity(saved);

        // Envoyer via WebSocket au destinataire
        UUID destinataireId =
                expediteurType ==
                        Message.ExpediteurType.COMPTABLE
                        ? conv.getClientId()
                        : conv.getComptableId();

        messagingTemplate.convertAndSendToUser(
                destinataireId.toString(),
                "/queue/messages",
                dto);

        // Aussi notifier l'expéditeur (confirmation)
        messagingTemplate.convertAndSendToUser(
                expediteurId.toString(),
                "/queue/messages",
                dto);

        // Notification Kafka pour email/push si hors ligne
        kafkaService.notifierNouveauMessage(
                dto, conv, expediteurType);

        log.info("Message envoyé: conv={} de={}",
                conversationId, expediteurNom);

        return dto;
    }

    // ── Marquer les messages comme lus ───────────────────
    @Transactional
    public void marquerLus(
            UUID conversationId,
            Message.ExpediteurType lecteurType) {

        int count = messageRepo.marquerTousLus(
                conversationId, lecteurType);

        Conversation conv = conversationRepo
                .findById(conversationId).orElse(null);
        if (conv != null) {
            if (lecteurType ==
                    Message.ExpediteurType.COMPTABLE) {
                conv.setNonLusComptable(0);
            } else {
                conv.setNonLusClient(0);
            }
            conversationRepo.save(conv);
        }
        log.info("AVANT: nonLusClient={}", conv.getNonLusClient());
        conv.setNonLusClient(0);
        conversationRepo.save(conv);
        log.info("APRÈS save: nonLusClient={}", conv.getNonLusClient());
        // Notifier via WebSocket que les messages sont lus
        messagingTemplate.convertAndSend(
                "/topic/conversation/" +
                        conversationId + "/lus",
                Map.of("conversationId", conversationId,
                        "lecteurType", lecteurType,
                        "count", count));

        log.info("{} messages marqués lus dans conv={}",
                count, conversationId);
    }

    // ── Récupérer les messages d'une conversation ─────────
    public List<MessageDto> getMessages(UUID conversationId) {
        return messageRepo
                .findByConversationIdOrderByCreatedAtAsc(
                        conversationId)
                .stream()
                .map(MessageDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ── Récupérer les conversations d'un comptable ────────
    public List<ConversationDto> getConversationsComptable(
            UUID comptableId) {
        return conversationRepo
                .findByComptableIdOrderByDateDernierMessageDesc(
                        comptableId)
                .stream()
                .map(ConversationDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ── Récupérer les conversations d'un client ───────────
    public List<ConversationDto> getConversationsClient(
            UUID clientId) {
        return conversationRepo
                .findByClientIdOrderByDateDernierMessageDesc(
                        clientId)
                .stream()
                .map(ConversationDto::fromEntity)
                .collect(Collectors.toList());
    }

    // ── Envoyer message avec déduction automatique ────────
    @Transactional
    public MessageDto envoyerMessageAuto(
            UUID conversationId,
            UUID expediteurId,
            String contenu,
            Message.TypeMessage typeMessage,
            String urlFichier,
            String nomFichier) {

        Conversation conv = conversationRepo
                .findById(conversationId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Conversation introuvable"));

        log.info("envoyerMessageAuto: expediteurId={} " +
                        "comptableId={} clientId={}",
                expediteurId,
                conv.getComptableId(),
                conv.getClientId());
        log.info("CHECK: expediteurId='{}' comptableId='{}' clientId='{}' egal_comptable={} egal_client={}",
                expediteurId,
                conv.getComptableId(),
                conv.getClientId(),
                expediteurId.equals(conv.getComptableId()),
                expediteurId.equals(conv.getClientId()));
        // Déduire automatiquement qui parle
        Message.ExpediteurType expediteurType;
        String expediteurNom;

        if (expediteurId.equals(conv.getComptableId())) {
            expediteurType = Message.ExpediteurType.COMPTABLE;
            expediteurNom  = conv.getComptableNom();
        } else if (expediteurId.equals(conv.getClientId())) {
            expediteurType = Message.ExpediteurType.CLIENT;
            expediteurNom  = conv.getClientNom();
        } else {
            log.warn("ExpéditeurId {} inconnu dans conv {}",
                    expediteurId, conversationId);
            expediteurType = Message.ExpediteurType.CLIENT;
            expediteurNom  = "Inconnu";
        }

        log.info("Message de {} ({}) dans conv={}",
                expediteurNom, expediteurType, conversationId);

        return envoyerMessage(
                conversationId,
                expediteurId,
                expediteurNom,
                expediteurType,
                contenu,
                typeMessage,
                urlFichier,
                nomFichier);
    }
}