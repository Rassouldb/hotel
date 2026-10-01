-- Extensions utilisées par le PMS (§40.1) :
--   btree_gist : contraintes d'exclusion anti-double réservation (§13.2)
--   pg_trgm    : recherche approximative des clients (§33.5)
--   pgcrypto   : chiffrement des données sensibles (§36.1)
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE organization (
    id          uuid PRIMARY KEY,
    name        varchar(200) NOT NULL,
    legal_name  varchar(200),
    country     varchar(2)   NOT NULL,
    status      varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0
);

CREATE TABLE hotel (
    id              uuid PRIMARY KEY,
    organization_id uuid         NOT NULL REFERENCES organization (id),
    code            varchar(20)  NOT NULL,
    name            varchar(200) NOT NULL,
    legal_name      varchar(200),
    address         varchar(500),
    phone           varchar(30),
    email           varchar(200),
    logo_url        varchar(500),
    legal_ids       jsonb,
    currency_code   varchar(3)   NOT NULL,
    timezone        varchar(50)  NOT NULL,
    language        varchar(10)  NOT NULL DEFAULT 'fr',
    business_date   date         NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_hotel_code UNIQUE (organization_id, code)
);

CREATE INDEX idx_hotel_organization ON hotel (organization_id);
