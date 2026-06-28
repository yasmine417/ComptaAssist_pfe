package com.comptaassist.document_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendDocumentUploaded(UUID documentId,
                                     UUID clientId,
                                     UUID cabinetId,
                                     String typeDocument) {
        Map<String, Object> event = Map.of(
                "documentId",   documentId.toString(),
                "clientId",     clientId != null
                        ? clientId.toString() : "",
                "cabinetId",    cabinetId.toString(),
                "typeDocument", typeDocument,
                "event",        "DOCUMENT_UPLOADED"
        );
        kafkaTemplate.send("document-events", event);
        log.info("Événement DOCUMENT_UPLOADED envoyé : {}",
                documentId);
    }
}