-- Nom de l'auteur conservé dans le journal : il reste lisible même si le compte est renommé ou supprimé.
ALTER TABLE audit_log ADD COLUMN user_name varchar(50);

CREATE INDEX idx_audit_log_org_date ON audit_log (organization_id, occurred_at DESC);
