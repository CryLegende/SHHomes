-- Table des homes de SHHomes avec SHNetwork. Le plugin la crée seul au démarrage si l'utilisateur a le droit CREATE ;
-- ce fichier sert à la créer à la main et à ne donner à l'utilisateur que les droits de lecture et d'écriture.
--
-- Utilisation (base bo_net pour les essais multi-Opale) :
--   mysql -u root < sql/schema.sql   puis remplacer CHANGE_ME par le mot de passe de config.yml

CREATE DATABASE IF NOT EXISTS bo_net;

CREATE TABLE IF NOT EXISTS bo_net.shhomes_homes (
    uuid     CHAR(36)     NOT NULL,              -- uuid « hors ligne » du joueur, le même sur tous les serveurs
    home_key CHAR(36)     NOT NULL,              -- clé stable du home (l'ID du home peut changer, pas cette clé)
    pos      INT          NOT NULL,              -- place dans la liste du joueur (ordre du menu)
    home_id  VARCHAR(190) NOT NULL,              -- ID du home (/home <id>)
    data     TEXT         NOT NULL,              -- home en JSON, comme dans data/<uuid>.json
    updated  BIGINT       NOT NULL,              -- dernière écriture, en millisecondes depuis 1970
    PRIMARY KEY (uuid, home_key),
    KEY idx_home_id (uuid, home_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE USER IF NOT EXISTS 'shhomes'@'127.0.0.1' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE, DELETE ON bo_net.shhomes_homes TO 'shhomes'@'127.0.0.1';
