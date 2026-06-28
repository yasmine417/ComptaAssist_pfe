package com.comptaassist.document_service.controller;

import com.comptaassist.document_service.dto.DocumentUploadResponse;
import com.comptaassist.document_service.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Slf4j
public class DocumentInternalController {

    private final MinioService minioService;

    @Value("${app.internal-secret:comptaassist-internal-secret}")
    private String internalSecret;

    // ── Upload PDF sans JWT — appel interne uniquement ────────────────────
    @PostMapping(value = "/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadInterne(
            @RequestParam("fichier")    MultipartFile fichier,
            @RequestParam("clientId")  String clientId,
            @RequestParam("cabinetId") String cabinetId,
            @RequestHeader("X-Internal-Secret") String secret) {

        // Vérifier le secret interne
        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(403)
                    .body(Map.of("erreur", "Accès refusé"));
        }

        try {
            String objectName = cabinetId + "/signatures_finales/" +
                    UUID.randomUUID() + ".pdf";

            minioService.uploader(fichier, objectName);
            log.info("PDF signé uploadé en interne : {}", objectName);

            return ResponseEntity.ok(Map.of("minioObject", objectName));
        } catch (Exception e) {
            log.error("Erreur upload interne : {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(Map.of("erreur", e.getMessage()));
        }
    }

    // ── URL presignée sans JWT ────────────────────────────────────────────
    @GetMapping("/documents/presigned-url")
    public ResponseEntity<Map<String, String>> presignedUrl(
            @RequestParam String object,
            @RequestHeader("X-Internal-Secret") String secret) {

        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(403)
                    .body(Map.of("erreur", "Accès refusé"));
        }

        String url = minioService.genererUrlPresignee(object);
        return ResponseEntity.ok(Map.of("url", url));
    }
}