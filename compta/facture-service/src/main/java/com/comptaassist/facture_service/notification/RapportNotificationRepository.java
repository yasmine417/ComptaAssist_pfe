package com.comptaassist.facture_service.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RapportNotificationRepository extends JpaRepository<com.comptaassist.facture_service.notification.RapportNotification, String> {
    List<com.comptaassist.facture_service.notification.RapportNotification> findByComptableIdAndLuFalseOrderByCreatedAtDesc(String comptableId);
    List<com.comptaassist.facture_service.notification.RapportNotification> findByComptableIdOrderByCreatedAtDesc(String comptableId);
    long countByComptableIdAndLuFalse(String comptableId);
}