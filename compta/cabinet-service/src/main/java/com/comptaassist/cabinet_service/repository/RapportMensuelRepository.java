package com.comptaassist.cabinet_service.repository;

import com.comptaassist.cabinet_service.entity.RapportMensuel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RapportMensuelRepository
        extends JpaRepository<RapportMensuel, UUID> {

    List<RapportMensuel> findByCabinetIdOrderByCreatedAtDesc(
            UUID cabinetId);

    List<RapportMensuel> findByClientIdOrderByCreatedAtDesc(
            UUID clientId);

    List<RapportMensuel> findByComptableIdOrderByCreatedAtDesc(
            UUID comptableId);
}