package com.comptaassist.document_service.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nom_fichier", nullable = false)
    private String nomFichier;

    @Column(name = "nom_original", nullable = false)
    private String nomOriginal;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_document", nullable = false)
    private DocumentType typeDocument;

    @Column(nullable = false)
    private Long taille;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "minio_bucket", nullable = false)
    private String minioBucket;

    @Column(name = "minio_object", nullable = false)
    private String minioObject;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "cabinet_id", nullable = false)
    private UUID cabinetId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "est_analyse", nullable = false)
    private boolean analyse = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}