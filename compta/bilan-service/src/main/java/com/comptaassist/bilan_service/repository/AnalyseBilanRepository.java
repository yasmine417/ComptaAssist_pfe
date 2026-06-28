// AnalyseBilanRepository.java
package com.comptaassist.bilan_service.repository;

import com.comptaassist.bilan_service.entity.AnalyseBilan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalyseBilanRepository
        extends JpaRepository<AnalyseBilan, UUID> {

    List<AnalyseBilan> findAllByClientIdOrderByExerciceDesc(
            UUID clientId);

    List<AnalyseBilan> findAllByCabinetId(UUID cabinetId);

    List<AnalyseBilan> findTop3ByClientIdOrderByExerciceDesc(
            UUID clientId);

    Optional<AnalyseBilan> findByClientIdAndExercice(
            UUID clientId, Integer exercice);

    boolean existsByClientIdAndExercice(
            UUID clientId, Integer exercice);
}