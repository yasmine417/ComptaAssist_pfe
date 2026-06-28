package com.comptaassist.cabinet_service.repository;

import com.comptaassist.cabinet_service.entity.SignatureElectronique;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SignatureElectroniqueRepository extends JpaRepository<SignatureElectronique, String> {

    List<SignatureElectronique> findByCabinetIdOrderByCreatedAtDesc(String cabinetId);

    List<SignatureElectronique> findByClientIdOrderByCreatedAtDesc(String clientId);

    List<SignatureElectronique> findByCabinetIdAndClientIdOrderByCreatedAtDesc(
            String cabinetId, String clientId);

    Optional<SignatureElectronique> findByOpenSignDocumentId(String openSignDocumentId);

    List<SignatureElectronique> findByStatut(SignatureElectronique.StatutSignature statut);
}