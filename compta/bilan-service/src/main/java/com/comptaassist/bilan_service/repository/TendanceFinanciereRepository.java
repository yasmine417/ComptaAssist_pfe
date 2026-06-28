// TendanceFinanciereRepository.java
package com.comptaassist.bilan_service.repository;

import com.comptaassist.bilan_service.entity.TendanceFinanciere;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TendanceFinanciereRepository
        extends JpaRepository<TendanceFinanciere, UUID> {

    List<TendanceFinanciere> findAllByClientIdOrderByDateDetectionDesc(
            UUID clientId);

    List<TendanceFinanciere> findAllByClientIdAndEstTraiteFalse(
            UUID clientId);

    List<TendanceFinanciere> findAllByCabinetIdAndEstTraiteFalse(
            UUID cabinetId);
}