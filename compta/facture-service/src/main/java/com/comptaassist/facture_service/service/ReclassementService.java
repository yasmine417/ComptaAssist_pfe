package com.comptaassist.facture_service.service;

import com.comptaassist.facture_service.dto.ReclassementRequest;
import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.repository.EcritureComptableRepository;
import com.comptaassist.facture_service.repository.FactureCPCRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReclassementService {

    private final FactureCPCRepository        factureRepo;
    private final EcritureComptableRepository ecritureRepo;
    private final JournalComptableService     journalService;
    private final CpcService                  cpcService;
    private final BilanService                bilanService; // ← ajouté
    private final ObjectMapper                mapper = new ObjectMapper();

    @Transactional
    public FactureCPC reclasser(ReclassementRequest req)
            throws Exception {

        UUID factureId = UUID.fromString(req.getFactureId());

        FactureCPC facture = factureRepo.findById(factureId)
                .orElseThrow(() -> new RuntimeException(
                        "Facture introuvable : " + factureId));

        String choix = req.getClassification().toUpperCase();

        if ("IMMOBILISATION".equals(choix)) {
            // Garder les écritures actuelles (déjà en 2355)
            facture.setConfirmationRequise(false);
            facture.setTypeEcriture("IMMOBILISATION");

            // ← Ajouter au BILAN
            bilanService.ajouterImmobilisation(facture);

            log.info("✅ Reclassement IMMOBILISATION confirmé "
                            + "→ ajouté au bilan pour facture {}",
                    factureId);

        } else if ("CHARGE".equals(choix)) {

            // ← Supprimer du bilan si existait
            bilanService.supprimerDuBilan(factureId);

            // Supprimer les écritures 2355 en BD
            ecritureRepo.deleteByFactureId(factureId);

            // Changer le type_operation et compte_charge
            String compteAlternatif =
                    facture.getCompteChargeAlternatif();
            String libelleAlternatif =
                    facture.getLibelleAlternatif();
            if (compteAlternatif == null
                    || compteAlternatif.isEmpty()) {
                compteAlternatif  = "6148";
                libelleAlternatif = "Autres charges externes";
            }

            facture.setTypeEcriture("CHARGE");
            facture.setCompteCharge(compteAlternatif);
            facture.setLibelleCompte(libelleAlternatif);
            facture.setConfirmationRequise(false);

            // Régénérer les écritures en classe 6
            Map<String, Object> donneesOcr = new HashMap<>();
            if (facture.getItemsJson() != null) {
                donneesOcr.put("items", mapper.readValue(
                        facture.getItemsJson(),
                        new TypeReference<List<Map<String, Object>>>() {}));
            }
            List<EcritureComptable> nouvellesEcritures =
                    journalService.genererEcrituresFactureCharge(
                            facture, donneesOcr);

            // Mettre à jour ecritureComptableJson
            List<Map<String, Object>> ecrituresMaps =
                    nouvellesEcritures.stream()
                            .map(e -> {
                                Map<String, Object> m =
                                        new LinkedHashMap<>();
                                m.put("num",     e.getNumLigne());
                                m.put("journal", e.getJournal());
                                m.put("date",    e.getDateEcriture() != null
                                        ? e.getDateEcriture().toString()
                                        : "");
                                m.put("piece",   e.getReferencePiece());
                                m.put("compte",  e.getCompte());
                                m.put("libelle", e.getLibelle());
                                m.put("desc",    e.getIntituleCompte());
                                m.put("debit",   e.getDebit() != null
                                        ? e.getDebit().doubleValue()
                                        : 0.0);
                                m.put("credit",  e.getCredit() != null
                                        ? e.getCredit().doubleValue()
                                        : 0.0);
                                m.put("sens",    e.getDebit() != null
                                        && e.getDebit().doubleValue() > 0
                                        ? "D" : "C");
                                return m;
                            })
                            .collect(java.util.stream.Collectors.toList());

            facture.setEcritureComptableJson(
                    mapper.writeValueAsString(ecrituresMaps));
            facture.setEcritureEquilibree(true);

            // Recalculer CPC depuis les nouvelles écritures
            Map<String, BigDecimal> totaux =
                    new LinkedHashMap<>();
            for (EcritureComptable e : nouvellesEcritures) {
                String compte = e.getCompte();
                if (compte != null && compte.startsWith("6")
                        && e.getDebit() != null
                        && e.getDebit().compareTo(
                        BigDecimal.ZERO) > 0) {
                    totaux.merge(compte, e.getDebit(),
                            BigDecimal::add);
                }
            }
            if (!totaux.isEmpty()) {
                String dateStr =
                        facture.getDateFacture() != null
                                ? facture.getDateFacture().toString()
                                : "";
                Map<String, Object> cpc =
                        cpcService.genererCpc(
                                totaux, dateStr, dateStr);
                facture.setCpcJson(
                        mapper.writeValueAsString(cpc));
            }

            log.info("✅ Reclassement CHARGE effectué "
                            + "— compte={} pour facture {}",
                    compteAlternatif, factureId);
        }

        return factureRepo.save(facture);
    }
}