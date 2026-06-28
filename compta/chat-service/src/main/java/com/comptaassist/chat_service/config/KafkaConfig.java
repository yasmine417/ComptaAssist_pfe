package com.comptaassist.chat_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_NOUVEAU_MESSAGE =
            "chat.nouveau-message";
    public static final String TOPIC_MESSAGE_LU =
            "chat.message-lu";

    @Bean
    public NewTopic topicNouveauMessage() {
        return TopicBuilder.name(TOPIC_NOUVEAU_MESSAGE)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic topicMessageLu() {
        return TopicBuilder.name(TOPIC_MESSAGE_LU)
                .partitions(3)
                .replicas(1)
                .build();
    }
}