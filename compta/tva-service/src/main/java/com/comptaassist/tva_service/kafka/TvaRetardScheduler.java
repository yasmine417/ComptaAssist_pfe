package com.comptaassist.tva_service.kafka;

import com.comptaassist.tva_service.entity.DeclarationTva;
import com.comptaassist.tva_service.entity.DeclarationTva.StatutDeclaration;
import com.comptaassist.tva_service.repository.DeclarationTvaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduler qui vérifie chaque jour à 8h00 les déclarations
 * TVA en retard et envoie une alerte Kafka.
 *
 * Logique :
 * - Déclaration BROUILLON + date limite dépassée → passer EN_RETARD + alerte
 * - Déclaration EN_RETARD → renvoyer l'alerte (rappel quotidien)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TvaRetardScheduler {

    private final DeclarationTvaRepository repo;
    private final TvaKafkaProducer         producer;

    /**
     * Exécuté tous les jours à 08:00.
     * cron = "seconde minute heure jour mois jour_semaine"
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void verifierDeclarationsEnRetard() {
        LocalDate today = LocalDate.now();
        log.info("🔍 Vérification déclarations TVA en retard — {}", today);

        // 1. Chercher toutes les déclarations BROUILLON dont la date limite est dépassée
        List<DeclarationTva> toutes = repo.findAll();
        int compteur = 0;

        for (DeclarationTva decl : toutes) {
            boolean brouillonEnRetard =
                    decl.getStatut() == StatutDeclaration.BROUILLON
                            && decl.getDateLimite() != null
                            && today.isAfter(decl.getDateLimite());

            boolean dejaEnRetard =
                    decl.getStatut() == StatutDeclaration.EN_RETARD;

            if (brouillonEnRetard) {
                // Mettre à jour le statut
                decl.setStatut(StatutDeclaration.EN_RETARD);
                repo.save(decl);
                log.warn("⚠️ Déclaration passée EN_RETARD : {} — {}",
                        decl.getPeriodeLabel(), decl.getClientId());
            }

            if (brouillonEnRetard || dejaEnRetard) {
                long joursRetard = ChronoUnit.DAYS.between(
                        decl.getDateLimite(), today);

                // Envoyer alerte Kafka
                TvaRetardEvent event = TvaRetardEvent.builder()
                        .declarationId(decl.getId())
                        .clientId(decl.getClientId())
                        .cabinetId(decl.getCabinetId())
                        .comptableId(decl.getComptableId())
                        .periodeLabel(decl.getPeriodeLabel())
                        .dateLimite(decl.getDateLimite())
                        .joursRetard((int) joursRetard)
                        .statut(StatutDeclaration.EN_RETARD.name())
                        .type("TVA_DECLARATION_EN_RETARD")
                        .build();

                producer.envoyerAlertRetard(event);
                compteur++;
            }
        }

        log.info("✅ Vérification terminée — {} alerte(s) envoyée(s)", compteur);
    }
}