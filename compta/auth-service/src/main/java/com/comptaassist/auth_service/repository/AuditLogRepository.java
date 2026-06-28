package com.comptaassist.auth_service.repository;

import com.comptaassist.auth_service.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(
            Pageable pageable);

    Page<AuditLog> findByUserEmailContainingIgnoreCase(
            String email, Pageable pageable);

    Page<AuditLog> findByAction(
            String action, Pageable pageable);
}