package com.comptaassist.auth_service.entity;



public enum StatutCompte {
    EN_ATTENTE,  // compte créé sans mot de passe
    ACTIF,       // mot de passe généré et envoyé
    DESACTIVE    // compte désactivé
}