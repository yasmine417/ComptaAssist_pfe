package com.comptaassist.cabinet_service.kafka;

// MembreCreatedProducer.java


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class MembreCreatedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendMembreCreated(UUID membreId, String email,
                                  String nom, String prenom) {
        Map<String, Object> event = Map.of(
                "membreId", membreId.toString(),
                "email",    email,
                "nom",      nom,
                "prenom",   prenom,
                "event",    "MEMBRE_CREATED"
        );
        kafkaTemplate.send("membre-events", event);
        log.info("Événement MEMBRE_CREATED envoyé pour : {}", email);
    }
}