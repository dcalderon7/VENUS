-- Procedimiento manual y explícito para habilitar el primer administrador.
-- Reemplaza únicamente el UUID por el id exacto de una cuenta previamente autorizada.
-- No ejecutar este archivo automáticamente desde la aplicación.

BEGIN;

WITH updated_user AS (
    UPDATE users
    SET role = 'admin'
    WHERE id = '19320fa0-c125-40af-b5e6-0a78b7bb86a6'::uuid
      AND role IS DISTINCT FROM 'admin'
    RETURNING id, username, email, role
)
SELECT id, username, email, role
FROM updated_user;

COMMIT;

-- Verificación manual posterior, usando el mismo UUID:
-- SELECT id, username, email, role
-- FROM users
-- WHERE id = 'REPLACE_WITH_AUTHORIZED_USER_UUID'::uuid;
