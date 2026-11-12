# Restablecimiento manual de contraseña

El script `reset-admin-password.js` es una operación local y explícita para la
cuenta administradora autorizada. No es una ruta HTTP ni forma parte de la
aplicación.

## Procedimiento

1. Ejecuta el comando desde la carpeta `server` en el equipo que tenga acceso a
   la base de datos:

   ```powershell
   cd "C:\Users\AMD\Downloads\Proyectos Android\Venus\server"
   node scripts\reset-admin-password.js
   ```

2. Escribe la nueva contraseña cuando se solicite. La entrada no se muestra.
   Confírmala en la segunda solicitud.
3. El script carga las variables de entorno de `server\.env`, usa `bcrypt` con
   10 rondas de salt, y actualiza únicamente `password_hash` del UUID
   autorizado.
4. Antes de actualizar, bloquea y comprueba la cuenta por UUID y `username`.
   Después exige que el `UPDATE` afecte exactamente una fila y verifica que
   `id`, `username`, `email` y `role` permanezcan sin cambios.
5. Si alguna comprobación falla, revierte la transacción. No imprime la
   contraseña, el hash ni tokens.

No ejecutes el comando hasta confirmar que `server\.env` apunta a la base de
datos correcta y que tienes autorización para modificar esa cuenta.

## Pruebas aisladas de PostgreSQL

Las pruebas deben usar una copia de `server\.env.test.example` llamada
`server\.env.test`, con un `DB_NAME` terminado en `_test` (por ejemplo,
`venus_test_db`). Al ejecutar Node con `NODE_ENV=test`, la configuración de
`src\config\db.js` carga únicamente ese archivo y rechaza la base real
`venus_db`. El puerto continúa siendo `5000`.

Los usuarios creados por las pruebas deben usar correos `@test.invalid`.
Los registros nuevos se crean como `collaborator`; ninguna cuenta obtiene
`admin` por defecto.

Para retirar cuentas de prueba antiguas de una base explícitamente autorizada:

```powershell
cd "C:\Users\AMD\Downloads\Proyectos Android\Venus\server"
node scripts\cleanup-watch-party-test-users.js --confirm
```

El script solo coincide con los prefijos de Watch Party indicados y correos
`@test.invalid`, elimina sus filas de `watchlist` y `watch_history`, y
desvincula `media_files` sin borrar archivos multimedia.
