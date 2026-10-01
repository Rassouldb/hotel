-- Journal d'audit en ajout seul (§32.1).
CREATE TABLE audit_log (
    id              uuid PRIMARY KEY,
    occurred_at     timestamptz  NOT NULL,
    organization_id uuid,
    hotel_id        uuid,
    user_id         uuid,
    action          varchar(100) NOT NULL,
    entity_type     varchar(100) NOT NULL,
    entity_id       varchar(100),
    old_value       jsonb,
    new_value       jsonb,
    reason          text,
    ip              varchar(64),
    user_agent      varchar(512),
    result          varchar(20)  NOT NULL CHECK (result IN ('SUCCESS', 'FAILURE')),
    error_code      varchar(100),
    trace_id        varchar(64)
);

CREATE INDEX idx_audit_log_hotel_date ON audit_log (hotel_id, occurred_at);
CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);

-- Toute modification ou suppression est refusée, quel que soit l'utilisateur de base de données.
CREATE FUNCTION audit_log_reject_change() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log est en ajout seul : % refusé', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_reject_change();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_reject_change();
