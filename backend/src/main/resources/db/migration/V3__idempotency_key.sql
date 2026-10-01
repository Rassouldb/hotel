-- Clés d'idempotence des opérations critiques, conservées 24 heures (§32.4).
CREATE TABLE idempotency_key (
    id              uuid PRIMARY KEY,
    scope           varchar(300) NOT NULL,
    idem_key        varchar(100) NOT NULL,
    request_hash    char(64)     NOT NULL,
    status          varchar(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status integer,
    response_body   jsonb,
    created_at      timestamptz  NOT NULL,
    completed_at    timestamptz,
    CONSTRAINT uq_idempotency_scope_key UNIQUE (scope, idem_key)
);

CREATE INDEX idx_idempotency_created_at ON idempotency_key (created_at);
