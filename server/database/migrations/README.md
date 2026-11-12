# Activación manual de migraciones de contraseñas

Estas migraciones son independientes y no se ejecutan desde Node.js:

- `001_admin_audit_log.sql` crea `admin_audit_log`, usada opcionalmente para
  registrar cambios administrativos de rol y solicitudes de restablecimiento.
- `002_password_reset_tokens.sql` crea `password_reset_tokens`, donde solo se
  almacena el hash SHA-256 de cada token, su expiración, uso y referencias a
  las cuentas implicadas.

Ambas migraciones dependen de la tabla existente `public.users`, pero no
dependen entre sí. Ejecutar una no crea ni modifica la otra. Usan `IF NOT
EXISTS` para que una repetición no duplique tablas o índices; antes de ejecutar
una tabla preexistente debe verificarse que su estructura sea compatible.

## Prevalidación de solo lectura

En pgAdmin, abre Query Tool sobre la base `venus_db` y ejecuta:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('users', 'admin_audit_log', 'password_reset_tokens')
ORDER BY table_name;

SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'users'
ORDER BY ordinal_position;
```

La tabla `users` debe existir y contener `id UUID`. En el estado revisado,
`admin_audit_log` y `password_reset_tokens` no existen.

## Ejecución desde pgAdmin

1. Confirma que el servidor y la base seleccionados sean los de `venus_db`.
2. Haz un respaldo de la base antes de aplicar cambios de esquema.
3. Abre `Query Tool`.
4. Abre y ejecuta **solo** `001_admin_audit_log.sql`.
5. Comprueba el resultado en el panel de mensajes.
6. Abre y ejecuta **solo** `002_password_reset_tokens.sql`.
7. Verifica las estructuras e índices con:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('admin_audit_log', 'password_reset_tokens')
ORDER BY table_name;

SELECT indexname, tablename
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('admin_audit_log', 'password_reset_tokens')
ORDER BY tablename, indexname;
```

No uses `DROP`, `TRUNCATE`, `DELETE` ni `UPDATE` para esta activación. Las
migraciones crean tablas e índices; no modifican filas existentes de `users`.

## Configuración SMTP

Las variables requeridas por `passwordService.js` son:

| Variable | Finalidad |
|---|---|
| `SMTP_HOST` | Host SMTP; Gmail: `smtp.gmail.com` |
| `SMTP_PORT` | `587` para STARTTLS o `465` para TLS directo |
| `SMTP_SECURE` | `false` con 587; `true` con 465 |
| `SMTP_USER` | Cuenta Gmail autorizada para enviar |
| `SMTP_PASSWORD` | Contraseña de aplicación de Google |
| `MAIL_FROM` | Remitente visible autorizado |
| `PUBLIC_WEB_URL` | URL pública de VenusWeb sin barra final |

En VenusWeb, configura además `VITE_API_URL` en su propio `.env` para apuntar
al backend, por ejemplo `VITE_API_URL=http://localhost:5000` en desarrollo o
`VITE_API_URL=https://api.dominio-real` en producción. `PUBLIC_WEB_URL` es la
URL que se inserta en el enlace del correo; `VITE_API_URL` es la URL que usa
el navegador para llamar a la API.

Para Gmail:

1. Usa una cuenta con verificación en dos pasos habilitada.
2. Crea una contraseña de aplicación en la cuenta de Google.
3. Coloca esa contraseña solo en el `.env` del servidor o en el gestor de
   secretos del entorno.
4. No la escribas en el código, repositorio, frontend, logs o chat.
5. Reinicia el backend después de cambiar `.env`.

Ejemplo de desarrollo:

```text
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=cuenta-de-envio@gmail.com
SMTP_PASSWORD=valor-generado-por-google
MAIL_FROM=VENUS <cuenta-de-envio@gmail.com>
PUBLIC_WEB_URL=http://localhost:5173
```

Ejemplo de producción:

```text
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=cuenta-de-envio@dominio-real
SMTP_PASSWORD=valor-generado-por-google
MAIL_FROM=VENUS <cuenta-de-envio@dominio-real>
PUBLIC_WEB_URL=https://venus.dominio-real
```

Los valores anteriores son ejemplos; no son credenciales reales. Esta fase no
envía correos ni ejecuta restablecimientos.

## URL y token

El backend construye el enlace como:

```text
PUBLIC_WEB_URL/reset-password#token=<token>
```

El token queda en el fragmento `#`, que el navegador no envía en la solicitud
HTTP al servidor. `ResetPasswordPage` lo lee en memoria y lo envía una sola vez
al endpoint de finalización; no lo guarda en `localStorage`, `sessionStorage` ni
logs. El servidor almacena únicamente su hash y lo invalida al usarlo o al
expirar.
