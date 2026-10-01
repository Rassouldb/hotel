-- Sessions de connexion et jetons de rafraîchissement à usage unique (§7.2).

-- Une session naît à la connexion ; ses jetons de rafraîchissement se succèdent (rotation).
CREATE TABLE user_session (
    id               uuid PRIMARY KEY,
    user_id          uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at       timestamptz  NOT NULL,
    last_activity_at timestamptz  NOT NULL,
    expires_at       timestamptz  NOT NULL,
    revoked_at       timestamptz,
    revoke_reason    varchar(40),
    ip               varchar(64),
    user_agent       varchar(512)
);

CREATE INDEX idx_user_session_user ON user_session (user_id) WHERE revoked_at IS NULL;

-- Seule l'empreinte SHA-256 du jeton est stockée.
CREATE TABLE refresh_token (
    id         uuid PRIMARY KEY,
    session_id uuid        NOT NULL REFERENCES user_session (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_session ON refresh_token (session_id);

CREATE TABLE password_reset_token (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);
