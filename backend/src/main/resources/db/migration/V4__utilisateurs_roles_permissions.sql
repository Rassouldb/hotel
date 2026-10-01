-- Utilisateurs, rôles et permissions (§7.3, §33.2).

CREATE TABLE app_user (
    id                   uuid PRIMARY KEY,
    organization_id      uuid         NOT NULL REFERENCES organization (id),
    username             varchar(50)  NOT NULL,
    first_name           varchar(100) NOT NULL,
    last_name            varchar(100) NOT NULL,
    email                varchar(200),
    phone                varchar(30),
    language             varchar(10)  NOT NULL DEFAULT 'fr',
    password_hash        varchar(255) NOT NULL,
    must_change_password boolean      NOT NULL DEFAULT true,
    super_admin          boolean      NOT NULL DEFAULT false,
    status               varchar(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    failed_login_count   integer      NOT NULL DEFAULT 0,
    locked_until         timestamptz,
    last_login_at        timestamptz,
    password_changed_at  timestamptz,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    created_by           uuid,
    updated_by           uuid,
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_app_user_username CHECK (username ~ '^[a-z0-9._-]{3,50}$')
);

CREATE UNIQUE INDEX uq_app_user_username ON app_user (username);
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_app_user_organization ON app_user (organization_id);

-- Catalogue des permissions, synchronisé au démarrage depuis le code (énumération Permission).
CREATE TABLE permission (
    code        varchar(60)  PRIMARY KEY,
    module      varchar(40)  NOT NULL,
    description varchar(255) NOT NULL
);

CREATE TABLE role (
    id              uuid PRIMARY KEY,
    organization_id uuid         NOT NULL REFERENCES organization (id),
    code            varchar(40)  NOT NULL,
    name            varchar(100) NOT NULL,
    description     varchar(255),
    system          boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_code UNIQUE (organization_id, code)
);

CREATE TABLE role_permission (
    role_id         uuid        NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    permission_code varchar(60) NOT NULL REFERENCES permission (code),
    PRIMARY KEY (role_id, permission_code)
);

-- Affectation d'un rôle à un utilisateur dans un hôtel.
CREATE TABLE user_hotel_role (
    user_id  uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    hotel_id uuid NOT NULL REFERENCES hotel (id),
    role_id  uuid NOT NULL REFERENCES role (id),
    PRIMARY KEY (user_id, hotel_id, role_id)
);

CREATE INDEX idx_user_hotel_role_hotel ON user_hotel_role (hotel_id);
CREATE INDEX idx_user_hotel_role_role ON user_hotel_role (role_id);

-- Journal des événements d'authentification (connexions, échecs, blocages, réinitialisations).
CREATE TABLE auth_event (
    id          uuid PRIMARY KEY,
    occurred_at timestamptz NOT NULL,
    user_id     uuid,
    username    varchar(100),
    event       varchar(40) NOT NULL,
    ip          varchar(64),
    user_agent  varchar(512),
    detail      varchar(255),
    trace_id    varchar(64)
);

CREATE INDEX idx_auth_event_user ON auth_event (user_id, occurred_at);

-- Auteur des modifications sur les tables existantes.
ALTER TABLE organization ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
ALTER TABLE hotel ADD COLUMN created_by uuid, ADD COLUMN updated_by uuid;
