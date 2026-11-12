# VENUS Backend Server

Servidor centralizado para la plataforma de streaming **VENUS**. Desarrollado con Node.js, Express, PostgreSQL, JWT y bcrypt.

## Requisitos Previos
- Node.js (v18+)
- PostgreSQL (v14+)

## Configuración e Instalación

1. Clonar o posicionarse en la carpeta `server/`.
2. Instalar dependencias:
   ```bash
   npm install
   ```
3. Crear un archivo `.env` basado en `.env.example` y configurar las credenciales de PostgreSQL y el secreto JWT.
4. Crear la base de datos en PostgreSQL:
   ```sql
   CREATE DATABASE venus_db;
   ```
5. Ejecutar el esquema SQL ubicado en `database/schema.sql` en tu base de datos `venus_db`.

## Ejecución

* **Modo Producción:**
  ```bash
  npm start
  ```
* **Modo Desarrollo (con auto-reload):**
  ```bash
  npm run dev
  ```

El servidor se iniciará por defecto en el puerto `5000`.

## Contraseñas

- `POST /api/user/password` requiere JWT y recibe `currentPassword`,
  `newPassword` y `confirmation`.
- `POST /api/admin/users/:id/password-reset` requiere un administrador y envía
  un enlace de un solo uso por correo. Nunca devuelve el token ni una
  contraseña.
- `POST /api/auth/password-reset/complete` recibe el token del enlace y la
  nueva contraseña.

El restablecimiento requiere ejecutar `database/migrations/002_password_reset_tokens.sql`
y configurar las variables SMTP de `.env`. Si falta la configuración SMTP, la
solicitud administrativa falla sin cambiar la contraseña.

La preparación paso a paso de PostgreSQL, Gmail SMTP y VenusWeb está en
`database/migrations/README.md`. Esta documentación no ejecuta migraciones ni
envía correos.

## Administración de usuarios

Los endpoints administrativos requieren un JWT válido y `adminMiddleware`.

- `GET /api/admin/users`: listado paginado; admite `search` y `role`.
- `GET /api/admin/users/:id`: detalle sin `password_hash`.
- `PATCH /api/admin/users/:id/role` con `{ "role": "admin|collaborator|user" }`.

El cambio de rol valida el rol en backend, consulta el permiso real en
PostgreSQL, impide modificar el propio rol y protege al último administrador
mediante una transacción con bloqueos de fila. La migración opcional
`database/migrations/001_admin_audit_log.sql` prepara una tabla de auditoría;
no se ejecuta automáticamente.

## Configuración para Android
* Si ejecutas en el emulador de Android Studio, utiliza la IP especial: `http://10.0.2.2:5000/api/`
* Si ejecutas en un dispositivo físico, utiliza la IP local de tu ordenador en la misma red Wi-Fi (ej: `http://192.168.1.X:5000/api/`).
