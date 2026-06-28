package com.comptaassist.auth_service.controller;

import com.comptaassist.auth_service.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @PostMapping("/log")
    public ResponseEntity<Void> logExterne(
            @RequestBody Map<String, String> body) {

        auditService.log(
                  null,
                body.get("userEmail"),
                body.get("userRole"),
                body.get("action"),
                body.get("objetType"),
                body.get("objetId"),
                body.get("details"),
                null
        );
        return ResponseEntity.ok().build();
    }
}