package com.comptaassist.tva_service.repository;

import com.comptaassist.tva_service.entity.ClientTvaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientTvaConfigRepository
        extends JpaRepository<ClientTvaConfig, UUID> {

    Optional<ClientTvaConfig> findByClientId(UUID clientId);

    List<ClientTvaConfig> findByCabinetId(UUID cabinetId);
}