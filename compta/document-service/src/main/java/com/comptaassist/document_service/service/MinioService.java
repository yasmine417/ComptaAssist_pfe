package com.comptaassist.document_service.service;

import com.comptaassist.document_service.exception.DocumentException;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public void creerBucketSiAbsent() {
        try {
            boolean existe = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build());
            if (!existe) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build());
                log.info("Bucket créé : {}", bucketName);
            }
        } catch (Exception e) {
            throw new DocumentException(
                    "Erreur création bucket : " + e.getMessage());
        }
    }

    public String uploader(MultipartFile fichier, String objectName) {
        try {
            creerBucketSiAbsent();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(fichier.getInputStream(),
                                    fichier.getSize(), -1)
                            .contentType(fichier.getContentType())
                            .build());
            log.info("Fichier uploadé dans MinIO : {}", objectName);
            return objectName;
        } catch (Exception e) {
            throw new DocumentException(
                    "Erreur upload MinIO : " + e.getMessage());
        }
    }

    public InputStream telecharger(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            throw new DocumentException(
                    "Erreur téléchargement MinIO : " + e.getMessage());
        }
    }

    public String genererUrlPresignee(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(1, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            throw new DocumentException(
                    "Erreur génération URL : " + e.getMessage());
        }
    }

    public void supprimer(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            log.info("Fichier supprimé de MinIO : {}", objectName);
        } catch (Exception e) {
            throw new DocumentException(
                    "Erreur suppression MinIO : " + e.getMessage());
        }
    }

    public String getBucketName() {
        return bucketName;
    }
}