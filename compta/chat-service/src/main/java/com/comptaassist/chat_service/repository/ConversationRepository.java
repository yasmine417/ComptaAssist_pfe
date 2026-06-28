package com.comptaassist.chat_service.repository;

import com.comptaassist.chat_service.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository
        extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByComptableIdOrderByDateDernierMessageDesc(
            UUID comptableId);

    List<Conversation> findByClientIdOrderByDateDernierMessageDesc(
            UUID clientId);

    Optional<Conversation> findByComptableIdAndClientId(
            UUID comptableId, UUID clientId);

    List<Conversation> findByCabinetIdOrderByDateDernierMessageDesc(
            UUID cabinetId);
}