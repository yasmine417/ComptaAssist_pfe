-- ════════════════════════════════════════════════
-- Migration V1 : Tables TVA marocaine
-- tva_db
-- ════════════════════════════════════════════════

-- ── Config TVA par client ─────────────────────
CREATE TABLE client_tva_config (
                                   id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   client_id        UUID NOT NULL UNIQUE,
                                   cabinet_id       UUID NOT NULL,
                                   comptable_id     UUID NOT NULL,
    -- ✅ VARCHAR (pas de type ENUM PostgreSQL — compatible Hibernate)
                                   regime           VARCHAR(20) NOT NULL DEFAULT 'MENSUEL',
                                   actif            BOOLEAN NOT NULL DEFAULT TRUE,
                                   created_at       TIMESTAMP DEFAULT NOW(),
                                   updated_at       TIMESTAMP DEFAULT NOW()
);

-- ── Déclarations TVA ─────────────────────────
CREATE TABLE declaration_tva (
                                 id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 client_id        UUID NOT NULL,
                                 cabinet_id       UUID NOT NULL,
                                 comptable_id     UUID NOT NULL,
    -- Période
                                 annee            INT NOT NULL,
                                 mois             INT,
                                 trimestre        INT,
                                 periode_label    VARCHAR(50),
                                 date_debut       DATE NOT NULL,
                                 date_fin         DATE NOT NULL,
                                 date_limite      DATE NOT NULL,
    -- Statut — VARCHAR compatible Hibernate @Enumerated(STRING)
                                 statut           VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
                                 date_soumission  TIMESTAMP,
                                 soumis_par       UUID,
    -- Montants
                                 tva_collectee_total   DECIMAL(15,2) DEFAULT 0,
                                 tva_deductible_total  DECIMAL(15,2) DEFAULT 0,
                                 tva_nette             DECIMAL(15,2) DEFAULT 0,
                                 credit_tva_reporte    DECIMAL(15,2) DEFAULT 0,
                                 notes            TEXT,
                                 created_at       TIMESTAMP DEFAULT NOW(),
                                 updated_at       TIMESTAMP DEFAULT NOW()
);

-- ── Lignes TVA par taux ───────────────────────
CREATE TABLE ligne_tva (
                           id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                           declaration_id   UUID NOT NULL REFERENCES declaration_tva(id) ON DELETE CASCADE,
                           taux             DECIMAL(5,2) NOT NULL,
    -- Achats
                           base_ht_achats   DECIMAL(15,2) DEFAULT 0,
                           tva_deductible   DECIMAL(15,2) DEFAULT 0,
    -- Ventes
                           base_ht_ventes   DECIMAL(15,2) DEFAULT 0,
                           tva_collectee    DECIMAL(15,2) DEFAULT 0,
    -- Compteurs
                           nb_factures_achat INT DEFAULT 0,
                           nb_factures_vente INT DEFAULT 0
);

-- ── Index ─────────────────────────────────────
CREATE INDEX idx_decl_client   ON declaration_tva(client_id);
CREATE INDEX idx_decl_cabinet  ON declaration_tva(cabinet_id);
CREATE INDEX idx_decl_statut   ON declaration_tva(statut);
CREATE INDEX idx_decl_periode  ON declaration_tva(annee, mois, trimestre);
CREATE INDEX idx_ligne_decl    ON ligne_tva(declaration_id);
CREATE INDEX idx_config_client ON client_tva_config(client_id);
CREATE INDEX idx_config_cabinet ON client_tva_config(cabinet_id);