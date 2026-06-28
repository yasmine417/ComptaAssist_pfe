package com.comptaassist.fiscal_rag_service.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents_indexes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIndexe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nomDocument;   // ex: CGI_2024

    @Column(nullable = false)
    private String nomFichier;    // ex: CGI_2024.pdf

    private Long tailleFichier;   // en bytes

    @Column(nullable = false)
    private String statut;        // OK, ERREUR, DEJA_INDEXE

    private String message;       // message retour Python

    private Integer nbMorceaux;   // nombre de blocs indexés

    private String adminId;       // qui a indexé

    private String adminEmail;    // email de l'admin

    @Column(nullable = false)
    private LocalDateTime indexeA;

    private LocalDateTime reindexeA; // si réindexation
}