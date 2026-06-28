package com.comptaassist.cabinet_service.repository;

// MembreRepository.java

import com.comptaassist.cabinet_service.entity.Membre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembreRepository extends JpaRepository<Membre, UUID> {
    List<Membre> findAllByCabinetId(UUID cabinetId);
    Optional<Membre> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Membre> findAllByCabinetIdAndActifTrue(UUID cabinetId);

    Optional<Membre> findByUserId(UUID userId);
    Optional<Membre> findByUserIdAndCabinetId(UUID userId, UUID cabinetId);
    boolean existsByEmailAndCabinetId(String email, UUID cabinetId);
}