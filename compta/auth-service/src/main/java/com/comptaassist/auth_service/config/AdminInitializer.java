package com.comptaassist.auth_service.config;

import com.comptaassist.auth_service.entity.Role;
import com.comptaassist.auth_service.entity.StatutCompte;
import com.comptaassist.auth_service.entity.User;
import com.comptaassist.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@comptaassist.ma";

        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Compte admin déjà existant, pas de création.");
            return;
        }

        User admin = User.builder()
                .nom("Admin")
                .prenom("System")
                .email(adminEmail)
                .password(passwordEncoder.encode("Admin@2025"))
                .role(Role.ADMIN)
                .statut(StatutCompte.ACTIF)
                .actif(true)
                .build();

        userRepository.save(admin);
        log.info("✅ Compte admin créé automatiquement : {}", adminEmail);
    }
}