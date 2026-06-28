package com.comptaassist.document_service.repository;

import com.comptaassist.document_service.entity.Document;
import com.comptaassist.document_service.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository
        extends JpaRepository<Document, UUID> {

    List<Document> findAllByCabinetId(UUID cabinetId);
    List<Document> findAllByClientIdAndAnalyseFalse(UUID clientId);
    List<Document> findAllByClientId(UUID clientId);

    List<Document> findAllByUploadedBy(UUID uploadedBy);

    List<Document> findAllByCabinetIdAndTypeDocument(
            UUID cabinetId, DocumentType type);

    List<Document> findAllByClientIdAndTypeDocument(
            UUID clientId, DocumentType type);


}