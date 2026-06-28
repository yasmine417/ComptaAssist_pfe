package com.comptaassist.cabinet_service.service;

// CabinetService.java


import com.comptaassist.cabinet_service.dto.CabinetRequest;
import com.comptaassist.cabinet_service.dto.CabinetResponse;
import com.comptaassist.cabinet_service.entity.Cabinet;
import com.comptaassist.cabinet_service.exception.CabinetException;
import com.comptaassist.cabinet_service.repository.CabinetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CabinetService {

    private final CabinetRepository cabinetRepository;

    @Transactional
    public CabinetResponse creer(CabinetRequest request, UUID directeurId) {
        if (cabinetRepository.existsByEmail(request.getEmail())) {
            throw new CabinetException("Email cabinet déjà utilisé");
        }
        Cabinet cabinet = Cabinet.builder()
                .nom(request.getNom())
                .email(request.getEmail())
                .telephone(request.getTelephone())
                .adresse(request.getAdresse())
                .directeurId(directeurId)
                .actif(true)
                .build();
        return toResponse(cabinetRepository.save(cabinet));
    }

    public CabinetResponse getByDirecteurId(UUID directeurId) {
        Cabinet cabinet = cabinetRepository
                .findByDirecteurId(directeurId)
                .orElseThrow(() ->
                        new CabinetException("Cabinet introuvable"));
        return toResponse(cabinet);
    }

    public CabinetResponse getById(UUID id) {
        return toResponse(cabinetRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Cabinet introuvable")));
    }

    @Transactional
    public CabinetResponse modifier(UUID id,
                                    CabinetRequest request,
                                    UUID directeurId) {
        Cabinet cabinet = cabinetRepository.findById(id)
                .orElseThrow(() ->
                        new CabinetException("Cabinet introuvable"));
        if (!cabinet.getDirecteurId().equals(directeurId)) {
            throw new CabinetException("Accès refusé");
        }
        cabinet.setNom(request.getNom());
        cabinet.setEmail(request.getEmail());
        cabinet.setTelephone(request.getTelephone());
        cabinet.setAdresse(request.getAdresse());
        return toResponse(cabinetRepository.save(cabinet));
    }

    private CabinetResponse toResponse(Cabinet c) {
        return CabinetResponse.builder()
                .id(c.getId())
                .nom(c.getNom())
                .email(c.getEmail())
                .telephone(c.getTelephone())
                .adresse(c.getAdresse())
                .directeurId(c.getDirecteurId())
                .actif(c.isActif())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
