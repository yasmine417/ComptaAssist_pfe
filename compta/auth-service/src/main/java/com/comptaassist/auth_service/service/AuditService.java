package com.comptaassist.auth_service.service;

import com.comptaassist.auth_service.entity.AuditLog;
import com.comptaassist.auth_service.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;

    // ← @Async retiré temporairement pour debug
    public void log(UUID userId,
                    String userEmail,
                    String userRole,
                    String action,
                    String objetType,
                    String objetId,
                    String details,
                    String ipAddress) {
        try {
            System.out.println("💾 Saving audit log : " + action
                    + " | " + userEmail);

            AuditLog al = AuditLog.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .userRole(userRole)
                    .action(action)
                    .objetType(objetType)
                    .objetId(objetId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .build();

            AuditLog saved = auditRepo.save(al);
            System.out.println("✅ Audit log sauvegardé id="
                    + saved.getId());

        } catch (Exception e) {
            System.out.println("❌ Erreur save audit : "
                    + e.getMessage());
            e.printStackTrace();
        }
    }

    public Page<AuditLog> getLogs(Pageable pageable) {
        return auditRepo.findAllByOrderByCreatedAtDesc(pageable);
    }

    public Page<AuditLog> getLogsByEmail(
            String email, Pageable pageable) {
        return auditRepo.findByUserEmailContainingIgnoreCase(
                email, pageable);
    }

    public Page<AuditLog> getLogsByAction(
            String action, Pageable pageable) {
        return auditRepo.findByAction(action, pageable);
    }
}