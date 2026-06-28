package com.comptaassist.document_service.service;

import com.comptaassist.document_service.dto.DocumentResponse;
import com.comptaassist.document_service.dto.DocumentUploadResponse;
import com.comptaassist.document_service.entity.Document;
import com.comptaassist.document_service.entity.DocumentType;
import com.comptaassist.document_service.exception.DocumentException;
import com.comptaassist.document_service.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final MinioService        minioService;

    @Transactional
    public DocumentUploadResponse uploader(
            MultipartFile fichier,
            String typeDocument,
            UUID clientId,
            UUID cabinetId,
            UUID uploadedBy) {

        // Vérifier que c'est un PDF
        if (!estPdf(fichier)) {
            throw new DocumentException(
                    "Seuls les fichiers PDF sont acceptés");
        }

        // Vérifier la taille max 50MB
        if (fichier.getSize() > 50 * 1024 * 1024) {
            throw new DocumentException(
                    "Fichier trop volumineux — max 50MB");
        }

        // Générer un nom unique pour MinIO
        String extension  = ".pdf";
        String objectName = cabinetId + "/"
                + typeDocument.toLowerCase() + "/"
                + UUID.randomUUID() + extension;

        // Uploader dans MinIO
        minioService.uploader(fichier, objectName);

        // Sauvegarder en base
        DocumentType type;
        try {
            type = DocumentType.valueOf(typeDocument.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = DocumentType.AUTRE;
        }

        Document document = Document.builder()
                .nomFichier(objectName)
                .nomOriginal(fichier.getOriginalFilename())
                .typeDocument(type)
                .taille(fichier.getSize())
                .contentType(fichier.getContentType())
                .minioBucket(minioService.getBucketName())
                .minioObject(objectName)
                .clientId(clientId)
                .cabinetId(cabinetId)
                .uploadedBy(uploadedBy)
                .analyse(false)
                .build();

        document = documentRepository.save(document);
        log.info("Document sauvegardé : {}", document.getId());

        return DocumentUploadResponse.builder()
                .id(document.getId())
                .message("Document uploadé avec succès")
                .nomOriginal(document.getNomOriginal())
                .typeDocument(document.getTypeDocument().name())
                .taille(document.getTaille())
                .minioObject(document.getMinioObject())
                .build();
    }

    public List<DocumentResponse> listerParClient(UUID clientId) {
        return documentRepository.findAllByClientId(clientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<DocumentResponse> listerParCabinet(UUID cabinetId) {
        return documentRepository.findAllByCabinetId(cabinetId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<DocumentResponse> listerParType(
            UUID cabinetId, String type) {
        DocumentType docType;
        try {
            docType = DocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DocumentException("Type invalide : " + type);
        }
        return documentRepository
                .findAllByCabinetIdAndTypeDocument(cabinetId, docType)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public DocumentResponse getById(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() ->
                        new DocumentException("Document introuvable"));
        return toResponse(doc);
    }

    public DocumentResponse getByIdAvecUrl(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() ->
                        new DocumentException("Document introuvable"));
        DocumentResponse response = toResponse(doc);
        // Générer URL de téléchargement valable 1 heure
        String url = minioService.genererUrlPresignee(
                doc.getMinioObject());
        response.setUrlTelechargement(url);
        return response;
    }

    @Transactional
    public void marquerAnalyse(UUID id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() ->
                        new DocumentException("Document introuvable"));
        doc.setAnalyse(true);
        documentRepository.save(doc);
    }

    @Transactional
    public void supprimer(UUID id, UUID uploadedBy) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() ->
                        new DocumentException("Document introuvable"));
        if (!doc.getUploadedBy().equals(uploadedBy)) {
            throw new DocumentException(
                    "Vous ne pouvez pas supprimer ce document");
        }
        minioService.supprimer(doc.getMinioObject());
        documentRepository.delete(doc);
        log.info("Document supprimé : {}", id);
    }

    private boolean estPdf(MultipartFile fichier) {
        String contentType = fichier.getContentType();
        String nom = fichier.getOriginalFilename();
        return (contentType != null
                && contentType.equals("application/pdf"))
                || (nom != null
                && nom.toLowerCase().endsWith(".pdf"));
    }

    private DocumentResponse toResponse(Document d) {
        return DocumentResponse.builder()
                .id(d.getId())
                .nomOriginal(d.getNomOriginal())
                .typeDocument(d.getTypeDocument().name())
                .taille(d.getTaille())
                .contentType(d.getContentType())
                .clientId(d.getClientId())
                .cabinetId(d.getCabinetId())
                .uploadedBy(d.getUploadedBy())
                .analyse(d.isAnalyse())
                .createdAt(d.getCreatedAt())
                .minioObject(d.getMinioObject())
                .build();
    }
}