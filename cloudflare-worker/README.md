# VENUS Cloudflare Worker

Este Worker sirve el build de `VenusWeb` y proxifica `/api/*` hacia la URL
HTTPS almacenada en KV bajo `BACKEND_URL`. Las respuestas se mantienen como
streaming, por lo que las cargas y los videos no se almacenan en Cloudflare.

La preparación inicial se ejecuta desde `Configurar_Cloudflare_VENUS.ps1`.
