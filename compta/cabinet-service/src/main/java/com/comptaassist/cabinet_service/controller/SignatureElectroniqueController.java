package com.comptaassist.cabinet_service.controller;

import com.comptaassist.cabinet_service.client.DocumentServiceClient;
import com.comptaassist.cabinet_service.entity.SignatureElectronique;
import com.comptaassist.cabinet_service.entity.SignatureElectronique.TypeDocument;
import com.comptaassist.cabinet_service.entity.SignatureLien;
import com.comptaassist.cabinet_service.repository.SignatureLienRepository;
import com.comptaassist.cabinet_service.service.SignatureElectroniqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/signatures")
@RequiredArgsConstructor
public class SignatureElectroniqueController {

    private final SignatureElectroniqueService service;
    private final SignatureLienRepository      lienRepo;
    private final DocumentServiceClient        documentClient;

    @PostMapping("/envoyer")
    public ResponseEntity<SignatureElectronique> envoyer(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authHeader) {

        String jwtToken = authHeader.replace("Bearer ", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> rawFormData = (Map<String, Object>) body.get("formData");
        Map<String, String> formData = new HashMap<>();
        if (rawFormData != null)
            rawFormData.forEach((k, v) -> formData.put(k, v != null ? v.toString() : ""));

        return ResponseEntity.ok(service.envoyerDocument(
                str(body, "cabinetId"), str(body, "clientId"),
                str(body, "clientEmail"), str(body, "clientNom"),
                TypeDocument.valueOf(str(body, "typeDocument")),
                formData, jwtToken
        ));
    }

    @PostMapping("/{id}/signer-comptable")
    public ResponseEntity<Map<String, String>> signerComptable(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader) {

        service.signerComptable(
                id,
                body.get("signature"),
                body.get("comptableNom"),
                authHeader.replace("Bearer ", "")
        );
        return ResponseEntity.ok(Map.of(
                "message", "Signature enregistrée, email envoyé au client"));
    }

    // ── Lien avec URL PDF signé pour le comptable ─────────────────────────
    @GetMapping("/{id}/lien")
    public ResponseEntity<Map<String, Object>> getLien(@PathVariable String id) {
        return lienRepo.findBySignatureId(id).stream().findFirst()
                .map(lien -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("statut", lien.getStatut().name());
                    result.put("signedAtComptable", lien.getSignedAtComptable() != null
                            ? lien.getSignedAtComptable().toString() : "");
                    result.put("signedAtClient", lien.getSignedAt() != null
                            ? lien.getSignedAt().toString() : "");
                    result.put("pdfSigneDisponible", lien.getPdfSigneMinioKey() != null);

                    // URL du PDF signé pour que le comptable puisse le voir
                    if (lien.getPdfSigneMinioKey() != null) {
                        result.put("urlPdfSigne",
                                documentClient.getUrlPresignee(lien.getPdfSigneMinioKey()));
                    }
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().<Map<String, Object>>build());
    }

    @GetMapping("/cabinet/{cabinetId}")
    public ResponseEntity<List<SignatureElectronique>> listerParCabinet(
            @PathVariable String cabinetId) {
        return ResponseEntity.ok(service.listerParCabinet(cabinetId));
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<SignatureElectronique>> listerParClient(
            @PathVariable String clientId) {
        return ResponseEntity.ok(service.listerParClient(clientId));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<SignatureElectronique> synchroniser(@PathVariable String id) {
        return ResponseEntity.ok(service.synchroniserStatut(id));
    }

    @PostMapping("/{id}/renvoyer")
    public ResponseEntity<Void> renvoyerEmail(@PathVariable String id) {
        service.renvoyerEmail(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/revoquer")
    public ResponseEntity<SignatureElectronique> revoquer(@PathVariable String id) {
        return ResponseEntity.ok(service.revoquer(id));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody Map<String, Object> payload) {
        service.traiterWebhook(payload);
        return ResponseEntity.ok().build();
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }
}