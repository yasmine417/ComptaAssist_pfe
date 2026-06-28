package com.comptaassist.auth_service.controller;




import com.comptaassist.auth_service.dto.UpdateProfileRequest;
import com.comptaassist.auth_service.dto.UserResponse;
import com.comptaassist.auth_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Récupérer son propre profil
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(userService.getByEmail(email));
    }

    // Modifier son propre profil
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal String email,
            @RequestBody UpdateProfileRequest request) {
        UserResponse user = userService.getByEmail(email);
        return ResponseEntity.ok(
                userService.updateProfile(user.getId(), request));
    }

    // Récupérer un utilisateur par id — ADMIN seulement
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    // Désactiver un compte — ADMIN seulement
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        userService.desactiverCompte(id);
        return ResponseEntity.noContent().build();
    }
}