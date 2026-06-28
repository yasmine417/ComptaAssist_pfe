package com.comptaassist.facture_service.service;

import io.minio.*;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:comptaassist}")
    private String bucket;

    @Value("${local.uploads.path:uploads}")
    private String dossierUploadsLocal;

    // ── Upload fichier (depuis MultipartFile) ─────────
    public String sauvegarder(MultipartFile fichier) {

        String ext = getExtension(fichier.getOriginalFilename());
        String objectName = "factures/" + UUID.randomUUID() + ext;

        byte[] fichierBytes;
        try {
            fichierBytes = fichier.getBytes();
        } catch (Exception e) {
            log.error("Erreur lecture fichier : {}", e.getMessage());
            return null;
        }

        // ── Sauvegarde locale SYSTÉMATIQUE, avant MinIO ──
        try {
            sauvegarderLocal(fichierBytes, objectName);
        } catch (Exception e) {
            log.warn("Sauvegarde locale échouée (non bloquant) : {}",
                    e.getMessage());
        }

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build());
                log.info("Bucket créé : {}", bucket);
            }

            try (InputStream is =
                         new java.io.ByteArrayInputStream(fichierBytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(is,
                                        fichierBytes.length,
                                        -1)
                                .contentType(
                                        fichier.getContentType() != null
                                                ? fichier.getContentType()
                                                : "application/pdf")
                                .build());
            }

            log.info("MinIO upload OK : {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("Erreur MinIO upload : {}", e.getMessage());
            // Le fichier est déjà sauvegardé localement, donc on retourne
            // quand même l'objectName pour pouvoir le retrouver plus tard
            return objectName;
        }
    }

    // ── URL présignée 24h ─────────────────────────────
    public String getUrlPresignee(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return "";
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs
                            .builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(24, TimeUnit.HOURS)
                            .extraQueryParams(Map.of(
                                    "response-content-type",        "application/pdf",
                                    "response-content-disposition", "inline"
                            ))
                            .build());
        } catch (Exception e) {
            log.error("Erreur URL présignée : {}", e.getMessage());
            return "";
        }
    }

    // ── Upload depuis bytes (utilisé par analyserDepuisToken) ──
    public String sauvegarderBytes(byte[] bytes, String nomOriginal) {

        String ext = getExtension(nomOriginal);
        String objectName = "factures/" + UUID.randomUUID() + ext;

        // ── Sauvegarde locale SYSTÉMATIQUE, avant MinIO ──
        try {
            sauvegarderLocal(bytes, objectName);
        } catch (Exception e) {
            log.warn("Sauvegarde locale échouée (non bloquant) : {}",
                    e.getMessage());
        }

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build());
                log.info("Bucket créé : {}", bucket);
            }

            String contentType;
            switch (ext.toLowerCase()) {
                case ".pdf"  -> contentType = "application/pdf";
                case ".png"  -> contentType = "image/png";
                case ".jpg",
                     ".jpeg" -> contentType = "image/jpeg";
                case ".webp" -> contentType = "image/webp";
                case ".tiff" -> contentType = "image/tiff";
                default      -> contentType = "application/octet-stream";
            }

            try (java.io.InputStream is =
                         new java.io.ByteArrayInputStream(bytes)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .stream(is, bytes.length, -1)
                                .contentType(contentType)
                                .build());
            }

            log.info("MinIO upload OK (bytes) : {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("Erreur MinIO sauvegarderBytes : {}", e.getMessage());
            // Le fichier est déjà sauvegardé localement
            return objectName;
        }
    }

    // ── Sauvegarde locale dans le dossier uploads ─────
    private void sauvegarderLocal(
            byte[] bytes, String objectName) throws Exception {

        Path dossier = Paths.get(dossierUploadsLocal);
        Path cheminFichier = dossier.resolve(objectName);
        Files.createDirectories(cheminFichier.getParent());
        Files.write(cheminFichier, bytes);

        log.info("Fichier sauvegardé localement : {}",
                cheminFichier.toAbsolutePath());
    }

    private String getExtension(String filename) {
        if (filename == null) return ".pdf";
        int idx = filename.lastIndexOf('.');
        return idx > 0
                ? filename.substring(idx)
                : ".pdf";
    }
}