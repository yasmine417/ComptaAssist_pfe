package com.comptaassist.facture_service.repository;

import com.comptaassist.facture_service.entity.FactureCPC;
import com.comptaassist.facture_service.entity.FactureCPC.StatutFacture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FactureCPCRepository
        extends JpaRepository<FactureCPC, UUID> {

    // ── Par comptable ─────────────────────────────────
    List<FactureCPC> findByComptableIdOrderByCreatedAtDesc(UUID comptableId);

    List<FactureCPC> findAllByComptableIdAndStatut(
            UUID comptableId, StatutFacture statut);

    long countByComptableIdAndStatut(
            UUID comptableId, StatutFacture statut);

    // ── Par client ────────────────────────────────────
    List<FactureCPC> findByClientIdOrderByDateFactureDesc(UUID clientId);

    List<FactureCPC> findByClientIdAndStatut(
            UUID clientId, StatutFacture statut);

    long countByClientIdAndStatut(
            UUID clientId, StatutFacture statut);

    // ── Par cabinet ───────────────────────────────────
    List<FactureCPC> findByCabinetIdOrderByDateFactureDesc(UUID cabinetId);
}