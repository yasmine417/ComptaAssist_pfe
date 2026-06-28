package com.comptaassist.cabinet_service.repository;

// CabinetRepository.java


import com.comptaassist.cabinet_service.entity.Cabinet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CabinetRepository extends JpaRepository<Cabinet, UUID> {
    Optional<Cabinet> findByDirecteurId(UUID directeurId);
    boolean existsByEmail(String email);
    List<Cabinet> findAllByActifTrue();
}