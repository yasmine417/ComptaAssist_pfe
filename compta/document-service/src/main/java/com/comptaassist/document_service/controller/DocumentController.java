package com.comptaassist.document_service.controller;

import com.comptaassist.document_service.dto.DocumentResponse;
import com.comptaassist.document_service.dto.DocumentUploadResponse;
import com.comptaassist.document_service.service.DocumentService;
import com.comptaassist.document_service.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final MinioService minioService;
    // Upload d'un document PDF
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploader(
            @RequestParam("fichier")      MultipartFile fichier,
            @RequestParam("typeDocument") String typeDocument,
            @RequestParam("cabinetId")    UUID cabinetId,
            @RequestParam(value = "clientId", required = false)
            UUID clientId,
            @AuthenticationPrincipal String uploadedBy) {

        return ResponseEntity.ok(
                documentService.uploader(
                        fichier,
                        typeDocument,
                        clientId,
                        cabinetId,
                        UUID.fromString(uploadedBy)));
    }

    // Lister les documents d'un client
    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<DocumentResponse>> listerParClient(
            @PathVariable UUID clientId) {
        return ResponseEntity.ok(
                documentService.listerParClient(clientId));
    }

    // Lister les documents d'un cabinet
    @GetMapping("/cabinet/{cabinetId}")
    public ResponseEntity<List<DocumentResponse>> listerParCabinet(
            @PathVariable UUID cabinetId) {
        return ResponseEntity.ok(
                documentService.listerParCabinet(cabinetId));
    }

    // Lister par type (BILAN, FACTURE, etc.)
    @GetMapping("/cabinet/{cabinetId}/type/{type}")
    public ResponseEntity<List<DocumentResponse>> listerParType(
            @PathVariable UUID cabinetId,
            @PathVariable String type) {
        return ResponseEntity.ok(
                documentService.listerParType(cabinetId, type));
    }

    // Consulter un document
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getById(id));
    }

    // Obtenir l'URL de téléchargement (valable 1 heure)
    @GetMapping("/{id}/telecharger")
    public ResponseEntity<DocumentResponse> telecharger(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                documentService.getByIdAvecUrl(id));
    }

    // Marquer un document comme analysé
    @PatchMapping("/{id}/analyser")
    public ResponseEntity<Void> marquerAnalyse(
            @PathVariable UUID id) {
        documentService.marquerAnalyse(id);
        return ResponseEntity.noContent().build();
    }

    // Supprimer un document
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(
            @PathVariable UUID id,
            @AuthenticationPrincipal String uploadedBy) {
        documentService.supprimer(
                id, UUID.fromString(uploadedBy));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @RequestParam String object) {
        String url = minioService.genererUrlPresignee(object);
        return ResponseEntity.ok(Map.of("url", url));
    }
}