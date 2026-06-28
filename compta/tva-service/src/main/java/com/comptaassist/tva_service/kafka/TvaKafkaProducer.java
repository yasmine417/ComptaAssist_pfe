package com.comptaassist.tva_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TvaKafkaProducer {

    private final KafkaTemplate<String, TvaRetardEvent> kafkaTemplate;

    @Value("${tva.kafka.topic.retard:tva-declarations-retard}")
    private String topicRetard;

    public void envoyerAlertRetard(TvaRetardEvent event) {
        try {
            kafkaTemplate.send(
                    topicRetard,
                    event.getClientId().toString(),  // clé = clientId
                    event);
            log.info("📨 Kafka → alerte retard TVA : client={} période={} joursRetard={}",
                    event.getClientId(),
                    event.getPeriodeLabel(),
                    event.getJoursRetard());
        } catch (Exception e) {
            log.error("❌ Erreur envoi Kafka alerte retard : {}", e.getMessage());
        }
    }
}