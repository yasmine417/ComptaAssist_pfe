package com.comptaassist.chat_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    // Qui envoie : COMPTABLE ou CLIENT
    @Enumerated(EnumType.STRING)
    @Column(name = "expediteur_type", nullable = false)
    private ExpediteurType expediteurType;

    @Column(name = "expediteur_id", nullable = false)
    private UUID expediteurId;

    @Column(name = "expediteur_nom")
    private String expediteurNom;

    @Column(nullable = false, length = 2000)
    private String contenu;

    @Column(nullable = false)
    @Builder.Default
    private boolean lu = false;

    @Column(name = "date_lecture")
    private LocalDateTime dateLecture;

    // Type de message
    @Enumerated(EnumType.STRING)
    @Column(name = "type_message")
    @Builder.Default
    private TypeMessage typeMessage = TypeMessage.TEXTE;

    // URL fichier joint si type = FICHIER
    @Column(name = "url_fichier", length = 2000)  // ← augmente de 255 à 2000
    private String urlFichier;

    @Column(name = "nom_fichier", length = 500)   // ← augmente aussi
    private String nomFichier;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum ExpediteurType {
        COMPTABLE, CLIENT
    }

    public enum TypeMessage {
        TEXTE, FICHIER, NOTIFICATION , IMAGE
    }
}