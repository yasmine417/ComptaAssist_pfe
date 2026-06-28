package com.comptaassist.facture_service.repository;

import com.comptaassist.facture_service.entity.EcritureComptable;
import com.comptaassist.facture_service.entity.EcritureComptable.StatutEcriture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EcritureComptableRepository
        extends JpaRepository<EcritureComptable, UUID> {

    // ── Par facture ───────────────────────────────────
    List<EcritureComptable> findByFactureIdOrderByNumLigne(
            UUID factureId);

    void deleteByFactureId(UUID factureId);

    // ── Par pièce (toutes les lignes d'une écriture) ──
    List<EcritureComptable> findByReferencePieceAndCabinetId(
            String referencePiece, UUID cabinetId);

    // ── Par journal et période ────────────────────────
    List<EcritureComptable> findByCabinetIdAndJournalAndDateEcritureBetween(
            UUID cabinetId, String journal,
            LocalDate debut, LocalDate fin);

    // ── Par exercice ──────────────────────────────────
    List<EcritureComptable> findByCabinetIdAndExercice(
            UUID cabinetId, String exercice);

    List<EcritureComptable>
    findByCabinetIdAndExerciceAndDateEcritureBetween(
            UUID cabinetId, String exercice,
            LocalDate debut, LocalDate fin);

    // ── Par compte (grand-livre) ──────────────────────
    List<EcritureComptable> findByCabinetIdAndCompteStartingWithOrderByDateEcriture(
            UUID cabinetId, String prefixeCompte);

    List<EcritureComptable>
    findByCabinetIdAndCompteAndDateEcritureBetweenOrderByDateEcriture(
            UUID cabinetId, String compte,
            LocalDate debut, LocalDate fin);

    // ── Totaux pour CPC ───────────────────────────────
    /**
     * Somme des débits/crédits par compte pour une période.
     * Utilisé pour générer le CPC automatiquement.
     */
    @Query("""
        SELECT e.compte,
               SUM(e.debit)  as totalDebit,
               SUM(e.credit) as totalCredit
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.dateEcriture BETWEEN :debut AND :fin
          AND (e.compte LIKE '6%' OR e.compte LIKE '7%')
        GROUP BY e.compte
        ORDER BY e.compte
        """)
    List<Object[]> findTotauxParComptePourCpc(
            @Param("cabinetId") UUID cabinetId,
            @Param("debut")     LocalDate debut,
            @Param("fin")       LocalDate fin);

    /**
     * Même chose filtré par exercice.
     */
    @Query("""
        SELECT e.compte,
               SUM(e.debit)  as totalDebit,
               SUM(e.credit) as totalCredit
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.exercice   = :exercice
          AND (e.compte LIKE '6%' OR e.compte LIKE '7%')
        GROUP BY e.compte
        ORDER BY e.compte
        """)
    List<Object[]> findTotauxParComptePourCpcExercice(
            @Param("cabinetId") UUID cabinetId,
            @Param("exercice")  String exercice);

    // ── Balance des comptes ───────────────────────────
    @Query("""
        SELECT e.compte,
               e.intituleCompte,
               SUM(e.debit)  as totalDebit,
               SUM(e.credit) as totalCredit,
               SUM(e.debit) - SUM(e.credit) as solde
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.dateEcriture BETWEEN :debut AND :fin
        GROUP BY e.compte, e.intituleCompte
        ORDER BY e.compte
        """)
    List<Object[]> findBalanceComptes(
            @Param("cabinetId") UUID cabinetId,
            @Param("debut")     LocalDate debut,
            @Param("fin")       LocalDate fin);

    // ── Tiers non lettrés (créances/dettes) ───────────
    @Query("""
        SELECT e FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.compte LIKE :prefixe%
          AND e.lettrage IS NULL
          AND e.statutEcriture <> 'EXTOURNEE'
        ORDER BY e.dateEcriture
        """)
    List<EcritureComptable> findNonLettreesParCompte(
            @Param("cabinetId") UUID cabinetId,
            @Param("prefixe")   String prefixe);

    // ── Numéro de séquence max pour un journal ─────────
    @Query("""
        SELECT MAX(e.numeroSequence)
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.journal   = :journal
          AND e.exercice  = :exercice
        """)
    Optional<String> findMaxSequence(
            @Param("cabinetId") UUID cabinetId,
            @Param("journal")   String journal,
            @Param("exercice")  String exercice);

    // ── Vérifier équilibre d'une pièce ────────────────
    @Query("""
        SELECT SUM(e.debit) - SUM(e.credit)
        FROM EcritureComptable e
        WHERE e.referencePiece = :ref
          AND e.cabinetId      = :cabinetId
        """)
    Double findEcartPiece(
            @Param("ref")       String ref,
            @Param("cabinetId") UUID cabinetId);

    // ── Compte des écritures par journal ──────────────
    long countByCabinetIdAndJournalAndExercice(
            UUID cabinetId, String journal, String exercice);

    // ── Solde trésorerie (comptes 514x/516x) ──────────
    @Query("""
        SELECT COALESCE(SUM(e.debit), 0) - COALESCE(SUM(e.credit), 0)
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND (e.compte LIKE '514%' OR e.compte LIKE '516%')
        """)
    Double findSoldeTresorerie(@Param("cabinetId") UUID cabinetId);

    // ── CPC filtré par client ─────────────────────────
    @Query("""
        SELECT e.compte,
               SUM(e.debit)  AS totalDebit,
               SUM(e.credit) AS totalCredit
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.clientId  = :clientId
          AND e.dateEcriture BETWEEN :debut AND :fin
          AND (e.compte LIKE '6%' OR e.compte LIKE '7%')
        GROUP BY e.compte
        ORDER BY e.compte
        """)
    List<Object[]> findTotauxParComptePourCpcEtClient(
            @Param("cabinetId") UUID cabinetId,
            @Param("debut")     LocalDate debut,
            @Param("fin")       LocalDate fin,
            @Param("clientId")  UUID clientId);

    @Query("""
        SELECT e.compte,
               SUM(e.debit)  AS totalDebit,
               SUM(e.credit) AS totalCredit
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.clientId  = :clientId
          AND e.exercice  = :exercice
          AND (e.compte LIKE '6%' OR e.compte LIKE '7%')
        GROUP BY e.compte
        ORDER BY e.compte
        """)
    List<Object[]> findTotauxParComptePourCpcExerciceEtClient(
            @Param("cabinetId") UUID cabinetId,
            @Param("exercice")  String exercice,
            @Param("clientId")  UUID clientId);

    // ── Balance filtrée par client ────────────────────
    @Query("""
        SELECT e.compte,
               e.intituleCompte,
               SUM(e.debit)  AS totalDebit,
               SUM(e.credit) AS totalCredit,
               SUM(e.debit) - SUM(e.credit) AS solde
        FROM EcritureComptable e
        WHERE e.cabinetId = :cabinetId
          AND e.clientId  = :clientId
          AND e.dateEcriture BETWEEN :debut AND :fin
        GROUP BY e.compte, e.intituleCompte
        ORDER BY e.compte
        """)
    List<Object[]> findBalanceComptesParClient(
            @Param("cabinetId") UUID cabinetId,
            @Param("clientId")  UUID clientId,
            @Param("debut")     LocalDate debut,
            @Param("fin")       LocalDate fin);
    List<EcritureComptable> findByCabinetIdAndDateEcritureBetweenOrderByDateEcritureAsc(
            UUID cabinetId, LocalDate debut, LocalDate fin);

    List<EcritureComptable> findByCabinetIdAndClientIdAndDateEcritureBetweenOrderByDateEcritureAsc(
            UUID cabinetId, UUID clientId, LocalDate debut, LocalDate fin);

    // Dans EcritureComptableRepository.java
// Ajouter cette méthode :

    List<EcritureComptable> findByCabinetIdAndClientIdAndCompteAndDateEcritureBetweenOrderByDateEcriture(
            UUID cabinetId, UUID clientId, String compte, LocalDate debut, LocalDate fin);

    List<EcritureComptable> findByClientIdOrderByCompteAsc(UUID clientId);

    List<EcritureComptable> findByClientIdAndExercice(
            UUID clientId, String exercice);
    Optional<EcritureComptable> findTopByClientIdAndExerciceOrderByDateEcritureDesc(
            UUID clientId, String exercice);


}