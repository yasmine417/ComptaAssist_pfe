-- Se connecter à la base auth_db pour insérer l'admin
\c auth_db;

INSERT INTO users (id, nom, prenom, email, password, role, statut, actif, created_at)
SELECT
    gen_random_uuid(),
    'Admin',
    'System',
    'admin@comptaassist.ma',
    '$2a$10$N.zmdr9zkooR9J5hOlMtY.8IEeWMFHEmJ4RVLnzDQzMJrEE08SQTO',
    'ADMIN',
    'ACTIF',
    true,
    NOW()
    WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'admin@comptaassist.ma'
);