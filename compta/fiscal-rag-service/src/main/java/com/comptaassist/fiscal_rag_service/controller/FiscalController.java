package com.comptaassist.fiscal_rag_service.controller;

import com.comptaassist.fiscal_rag_service.dto.ConversationRAGDto;
import com.comptaassist.fiscal_rag_service.dto.DocumentIndexeDto;
import com.comptaassist.fiscal_rag_service.dto.IndexerRequest;
import com.comptaassist.fiscal_rag_service.dto.QuestionRequest;
import com.comptaassist.fiscal_rag_service.dto.ReponseRAG;
import com.comptaassist.fiscal_rag_service.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fiscal")
@RequiredArgsConstructor
public class FiscalController {

    private final RagService ragService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "ok",
                "service", "fiscal-rag-service"
        ));
    }

    @PostMapping("/question")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<ReponseRAG> poserQuestion(
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal String comptableId) {
        return ResponseEntity.ok(
                ragService.poserQuestion(
                        request.getQuestion(), comptableId));
    }

    @PostMapping("/question-client")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<ReponseRAG> poserQuestionClient(
            @RequestParam UUID clientId,
            @RequestParam(required = false) String mois,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal String comptableId,
            HttpServletRequest httpRequest) {

        String jwtToken = extraireToken(httpRequest);

        String moisFinal = (mois != null && !mois.isBlank())
                ? mois
                : YearMonth.now().minusMonths(1).toString();

        return ResponseEntity.ok(
                ragService.poserQuestionClient(
                        request.getQuestion(),
                        comptableId,
                        clientId,
                        moisFinal,
                        jwtToken));
    }

    @GetMapping("/statut")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR','ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> statut() {
        return ResponseEntity.ok(ragService.getStatut());
    }

    @PostMapping("/indexer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> indexer(
            @RequestParam(required = false) String cheminPdf,
            @RequestParam(required = false) String nomDocument,
            @RequestBody(required = false) IndexerRequest body) {

        String chemin = cheminPdf != null
                ? cheminPdf
                : (body != null ? body.getCheminPdf() : null);
        String nom = nomDocument != null
                ? nomDocument
                : (body != null ? body.getNomDocument() : null);

        if (chemin == null || nom == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("statut", "erreur",
                            "message", "cheminPdf et nomDocument requis"));
        }
        return ResponseEntity.ok(ragService.indexerDocument(chemin, nom));
    }

    @PostMapping("/upload-indexer")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadEtIndexer(
            @RequestParam("fichier") MultipartFile fichier,
            @RequestParam("nom_document") String nomDocument,
            @RequestParam(value = "forcer", defaultValue = "false") boolean forcer,
            @AuthenticationPrincipal String adminId) {
        return ResponseEntity.ok(
                ragService.uploadEtIndexer(
                        fichier, nomDocument, forcer,
                        adminId, adminId));
    }

    @GetMapping("/historique")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<DocumentIndexeDto>> historique() {
        return ResponseEntity.ok(ragService.getHistorique());
    }

    @GetMapping("/historique-conversations")
    @PreAuthorize("hasAnyAuthority('ROLE_COMPTABLE','ROLE_DIRECTEUR')")
    public ResponseEntity<List<ConversationRAGDto>> historiqueConversations(
            @AuthenticationPrincipal String comptableId) {
        return ResponseEntity.ok(
                ragService.getHistoriqueConversations(comptableId));
    }

    private String extraireToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : "";
    }
}