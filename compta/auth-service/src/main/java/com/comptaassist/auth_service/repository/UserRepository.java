package com.comptaassist.auth_service.repository;



import com.comptaassist.auth_service.entity.Role;
import com.comptaassist.auth_service.entity.StatutCompte;
import com.comptaassist.auth_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByStatut(StatutCompte statut);
    List<User> findByRole(Role role);
}