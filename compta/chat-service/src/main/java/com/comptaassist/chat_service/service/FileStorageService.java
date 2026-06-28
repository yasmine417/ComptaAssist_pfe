package com.comptaassist.chat_service.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FileStorageService {

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${local.uploads.path:uploads}")
    private String dossierUploadsLocal;

    private MinioClient getClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }

    public String uploadFichier(
            MultipartFile file,
            String conversationId) throws Exception {

        String ext = "";
        String originalName = file.getOriginalFilename();
        if (originalName != null
                && originalName.contains(".")) {
            ext = originalName.substring(
                    originalName.lastIndexOf("."));
        }

        String objectName = "conv-" + conversationId
                + "/" + UUID.randomUUID() + ext;

        byte[] fileBytes = file.getBytes();

        // ── Sauvegarde locale systématique (peu importe MinIO) ──
        try {
            sauvegarderLocal(fileBytes, conversationId,
                    originalName != null ? originalName : "fichier" + ext);
        } catch (Exception e) {
            log.warn("Sauvegarde locale échouée (non bloquant) : {}",
                    e.getMessage());
        }

        MinioClient client = getClient();

        // Créer le bucket si inexistant
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket).build());
        if (!exists) {
            client.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket).build());
        }

        try (java.io.ByteArrayInputStream is =
                     new java.io.ByteArrayInputStream(fileBytes)) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(is, fileBytes.length, -1)
                            .contentType(file.getContentType())
                            .build());
        }

        // URL présignée valable 7 jours
        return client.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .method(Method.GET)
                        .expiry(7, TimeUnit.DAYS)
                        .build());
    }

    // ── Sauvegarde locale dans uploads/chat/conv-{id}/ ─────────
    private void sauvegarderLocal(
            byte[] bytes, String conversationId, String nomOriginal)
            throws Exception {

        Path dossier = Paths.get(
                dossierUploadsLocal, "chat",
                "conv-" + conversationId);
        Files.createDirectories(dossier);

        String nomUnique = UUID.randomUUID() + "_" + nomOriginal;
        Path cheminFichier = dossier.resolve(nomUnique);
        Files.write(cheminFichier, bytes);

        log.info("Fichier chat sauvegardé localement : {}",
                cheminFichier.toAbsolutePath());
    }
}