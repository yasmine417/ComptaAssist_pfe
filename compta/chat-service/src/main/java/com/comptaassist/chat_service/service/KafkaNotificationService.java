package com.comptaassist.chat_service.service;

import com.comptaassist.chat_service.config.KafkaConfig;
import com.comptaassist.chat_service.dto.MessageDto;
import com.comptaassist.chat_service.entity.Conversation;
import com.comptaassist.chat_service.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaNotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void notifierNouveauMessage(
            MessageDto message,
            Conversation conv,
            Message.ExpediteurType expediteurType) {

        Map<String, Object> event = new HashMap<>();
        event.put("messageId",
                message.getId().toString());
        event.put("conversationId",
                message.getConversationId().toString());
        event.put("expediteurNom",
                message.getExpediteurNom());
        event.put("expediteurType",
                expediteurType.name());
        event.put("contenu",
                message.getContenu().length() > 100
                        ? message.getContenu()
                        .substring(0, 100) + "..."
                        : message.getContenu());

        // Destinataire email pour notification
        if (expediteurType ==
                Message.ExpediteurType.COMPTABLE) {
            event.put("destinataireEmail",
                    conv.getClientEmail());
            event.put("destinataireNom",
                    conv.getClientNom());
        } else {
            event.put("destinataireEmail",
                    conv.getComptableEmail());
            event.put("destinataireNom",
                    conv.getComptableNom());
        }

        kafkaTemplate.send(
                KafkaConfig.TOPIC_NOUVEAU_MESSAGE,
                conv.getId().toString(),
                event);

        log.info("Kafka notification envoyée: conv={}",
                conv.getId());
    }
}