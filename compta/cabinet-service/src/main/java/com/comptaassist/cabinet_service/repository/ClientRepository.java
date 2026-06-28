package com.comptaassist.cabinet_service.repository;

// ClientRepository.java


import com.comptaassist.cabinet_service.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    List<Client> findAllByCabinetId(UUID cabinetId);
    List<Client> findAllByComptableId(UUID comptableId);
    Optional<Client> findByNumeroFiscal(String numeroFiscal);
    boolean existsByNumeroFiscal(String numeroFiscal);
    List<Client> findAllByCabinetIdAndActifTrue(UUID cabinetId);

    List<Client> findAllByCabinetIdAndComptableIdAndActifTrue(
            UUID cabinetId, UUID comptableId);
    List<Client> findByActifTrue();
}