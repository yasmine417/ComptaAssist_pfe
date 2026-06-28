package com.comptaassist.tva_service.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling  // ✅ Active les @Scheduled
public class KafkaConfig {

    /**
     * Crée le topic automatiquement au démarrage
     * si Kafka est disponible.
     */
    @Bean
    public NewTopic topicRetardTva() {
        return TopicBuilder
                .name("tva-declarations-retard")
                .partitions(3)
                .replicas(1)
                .build();
    }
}