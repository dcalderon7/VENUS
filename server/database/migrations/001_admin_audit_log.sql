-- Migración independiente para auditar cambios administrativos de rol.
-- Ejecutar manualmente después de revisar el esquema y el procedimiento de despliegue.

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(100) NOT NULL,
    previous_role VARCHAR(50) NOT NULL,
    new_role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at
    ON admin_audit_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_target_user_id
    ON admin_audit_log(target_user_id);
