package com.comptaassist.fiscal_rag_service.repository;

import com.comptaassist.fiscal_rag_service.entity.ConversationRAG;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ConversationRAGRepository
        extends JpaRepository<ConversationRAG, UUID> {

    List<ConversationRAG> findByComptableIdOrderByPoseeADesc(String comptableId);
}