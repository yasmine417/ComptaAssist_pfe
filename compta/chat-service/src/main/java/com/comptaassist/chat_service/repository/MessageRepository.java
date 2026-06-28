package com.comptaassist.chat_service.repository;

import com.comptaassist.chat_service.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface MessageRepository
        extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(
            UUID conversationId);

    long countByConversationIdAndLuFalse(UUID conversationId);

    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.lu = true, " +
            "m.dateLecture = CURRENT_TIMESTAMP " +
            "WHERE m.conversationId = :convId " +
            "AND m.expediteurType != :type " +
            "AND m.lu = false")
    int marquerTousLus(
            UUID convId,
            Message.ExpediteurType type);
}