package com.comptaassist.facture_service.repository;

import com.comptaassist.facture_service.entity
        .LienClientUpload;
import org.springframework.data.jpa.repository
        .JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface LienClientUploadRepository
        extends JpaRepository<LienClientUpload, UUID> {

    Optional<LienClientUpload> findByToken(
            String token);

    List<LienClientUpload> findAllByComptableId(
            UUID comptableId);

    List<LienClientUpload> findAllByClientId(
            UUID clientId);
}