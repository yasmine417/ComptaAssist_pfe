package com.comptaassist.cabinet_service.repository;

import com.comptaassist.cabinet_service.entity.SignatureLien;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SignatureLienRepository extends JpaRepository<SignatureLien, String> {
    Optional<SignatureLien> findByToken(String token);
    List<SignatureLien> findBySignatureId(String signatureId);
}