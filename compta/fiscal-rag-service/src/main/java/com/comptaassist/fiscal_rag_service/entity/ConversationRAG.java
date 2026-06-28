package com.comptaassist.fiscal_rag_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations_rag")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRAG {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String comptableId;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reponse;

    @ElementCollection
    @CollectionTable(name = "conversation_sources",
            joinColumns = @JoinColumn(name = "conversation_id"))
    @Column(name = "source")
    private List<String> sources;

    private Integer nbExtraits;

    @Column(nullable = false)
    private LocalDateTime poseeA;
}