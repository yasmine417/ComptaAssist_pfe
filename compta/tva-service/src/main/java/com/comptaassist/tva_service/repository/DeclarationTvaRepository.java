package com.comptaassist.tva_service.repository;

import com.comptaassist.tva_service.entity.DeclarationTva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeclarationTvaRepository
        extends JpaRepository<DeclarationTva, UUID> {

    List<DeclarationTva> findByCabinetId(UUID cabinetId);

    List<DeclarationTva> findByClientIdOrderByDateDebutDesc(UUID clientId);

    @Query("""
        SELECT d FROM DeclarationTva d
        WHERE d.clientId = :clientId
          AND d.annee = :annee
          AND (:mois IS NULL OR d.mois = :mois)
          AND (:trimestre IS NULL OR d.trimestre = :trimestre)
        """)
    Optional<DeclarationTva> findByClientIdAndAnneeMoisTrimestre(
            @Param("clientId")   UUID clientId,
            @Param("annee")      Integer annee,
            @Param("mois")       Integer mois,
            @Param("trimestre")  Integer trimestre);
}