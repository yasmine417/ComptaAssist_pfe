package com.comptaassist.fiscal_rag_service.repository;

import com.comptaassist.fiscal_rag_service.entity.DocumentIndexe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentIndexeRepository
        extends JpaRepository<DocumentIndexe, UUID> {

    List<DocumentIndexe> findAllByOrderByIndexeADesc();
    Optional<DocumentIndexe> findByNomDocument(String nomDocument);
}