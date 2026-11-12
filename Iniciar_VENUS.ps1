$ErrorActionPreference = "Stop"

$venus = "C:\Users\AMD\Downloads\Proyectos Android\Venus"
$server = "$venus\server"
$localConfig = "$venus\Venus.cloudflare.local.ps1"

$logs = "$venus\logs_inicio"
New-Item -ItemType Directory -Force -Path $logs | Out-Null

$backendLog = "$logs\backend.log"
$backendErrorLog = "$logs\backend_error.log"
$tunnelBackendLog = "$logs\tunnel_backend.log"

$procesos = @()

if (-not (Test-Path $localConfig)) {
    throw "No existe la configuración local de Cloudflare: $localConfig. Ejecutá Configurar_Cloudflare_VENUS.ps1 primero."
}
. $localConfig

if ([string]::IsNullOrWhiteSpace($WorkerUrl) -or [string]::IsNullOrWhiteSpace($AdminRegistrationSecret)) {
    throw "La configuración local de Cloudflare está incompleta. Ejecutá Configurar_Cloudflare_VENUS.ps1."
}
if ($WorkerUrl -notmatch '^https://(?:[a-z0-9-]+\.)+workers\.dev/?$') {
    throw "WorkerUrl no es una URL workers.dev válida."
}
if ($AdminRegistrationSecret.Length -lt 32) {
    throw "AdminRegistrationSecret debe tener al menos 32 caracteres."
}

function Iniciar-Proceso($comando, $directorio, $log) {
    $comandoConLog = "$comando > `"$log`" 2>&1"
    $argumentos = @('/d', '/v:on', '/s', '/c', $comandoConLog)
    $p = Start-Process cmd.exe -ArgumentList $argumentos -WorkingDirectory $directorio -WindowStyle Hidden -PassThru
    $script:procesos += $p
    return $p
}

function Iniciar-Backend {
    $p = Start-Process node `
        -ArgumentList @('src/server.js') `
        -WorkingDirectory $server `
        -RedirectStandardOutput $backendLog `
        -RedirectStandardError $backendErrorLog `
        -WindowStyle Hidden `
        -PassThru
    $script:procesos += $p
    return $p
}

function Obtener-PropietarioPuerto($puerto) {
    $listeners = @(Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue)
    foreach ($listener in $listeners) {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction SilentlyContinue
        [pscustomobject]@{
            Port = $puerto
            PID = $listener.OwningProcess
            Name = $process.Name
            CommandLine = $process.CommandLine
        }
    }
}

function Confirmar-PuertoDisponible($puerto) {
    $owners = @(Obtener-PropietarioPuerto $puerto)
    if ($owners.Count -gt 0) {
        $details = ($owners | ForEach-Object {
            "PID $($_.PID) ($($_.Name)): $($_.CommandLine)"
        }) -join '; '
        throw "El puerto $puerto ya está ocupado. No se detendrá el proceso existente: $details"
    }
}

function Esperar-PostgreSQL($segundos) {
    for ($i = 0; $i -lt $segundos; $i++) {
        $test = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue
        if ($test.TcpTestSucceeded) {
            return
        }

        Start-Sleep -Seconds 1
    }

    $services = @(Get-Service -ErrorAction SilentlyContinue | Where-Object {
        $_.Name -match 'postgres' -or $_.DisplayName -match 'postgres'
    })
    $stopped = @($services | Where-Object { $_.Status -eq 'Stopped' })

    if ($stopped.Count -eq 1) {
        Write-Host "PostgreSQL no estaba activo. Iniciando servicio $($stopped[0].Name)..." -ForegroundColor Yellow
        Start-Service -Name $stopped[0].Name

        for ($i = 0; $i -lt $segundos; $i++) {
            $test = Test-NetConnection -ComputerName 127.0.0.1 -Port 5432 -WarningAction SilentlyContinue
            if ($test.TcpTestSucceeded) {
                return
            }

            Start-Sleep -Seconds 1
        }
    }

    throw "PostgreSQL no está disponible en 127.0.0.1:5432."
}

function Obtener-URL($archivo) {
    if (Test-Path $archivo) {
        $contenido = Get-Content $archivo -Raw -ErrorAction SilentlyContinue

        if (-not [string]::IsNullOrWhiteSpace($contenido)) {
            $match = [regex]::Match(
                $contenido,
                'https://[a-z0-9-]+\.trycloudflare\.com',
                [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
            )

            if ($match.Success) {
                return $match.Value
            }
        }
    }

    return $null
}

function Esperar-Puerto($puerto, $segundos) {
    Write-Host "Esperando puerto $puerto..." -ForegroundColor Yellow

    for ($i = 0; $i -lt $segundos; $i++) {
        $cliente = New-Object System.Net.Sockets.TcpClient

        try {
            $resultado = $cliente.BeginConnect("127.0.0.1", $puerto, $null, $null)
            $conectado = $resultado.AsyncWaitHandle.WaitOne(500, $false)

            if ($conectado -and $cliente.Connected) {
                $cliente.EndConnect($resultado)
                $cliente.Close()
                return $true
            }
        }
        catch {
        }
        finally {
            $cliente.Close()
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Esperar-URL($archivo, $segundos, $descripcion) {
    Write-Host "Esperando URL de $descripcion..." -ForegroundColor Yellow

    for ($i = 0; $i -lt $segundos; $i++) {
        $url = Obtener-URL $archivo

        if ($url) {
            return $url
        }

        Start-Sleep -Seconds 1
    }

    return $null
}

function Esperar-HTTP($url, $segundos, $descripcion) {
    Write-Host "Validando $descripcion..." -ForegroundColor Yellow

    for ($i = 0; $i -lt $segundos; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
            return $response
        }
        catch {
            if ($_.Exception.Response) {
                return $_.Exception.Response
            }
        }

        Start-Sleep -Seconds 1
    }

    return $null
}

function Registrar-Backend($workerUrl, $backendUrl) {
    try {
        return Invoke-WebRequest `
            -UseBasicParsing `
            -Method Post `
            -Uri "$workerUrl/__admin/register" `
            -Headers @{ Authorization = "Bearer $AdminRegistrationSecret" } `
            -ContentType "application/json" `
            -Body (@{ backendUrl = $backendUrl } | ConvertTo-Json) `
            -TimeoutSec 15
    }
    catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            throw "El registro KV del backend falló con HTTP $status."
        }
        throw "No se pudo contactar al endpoint administrativo del Worker: $($_.Exception.Message)"
    }
}

function Esperar-WorkerAPI($workerUrl, $segundos) {
    Write-Host "Esperando propagación de KV..." -ForegroundColor Yellow

    for ($i = 0; $i -lt $segundos; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$workerUrl/api/auth/me" -TimeoutSec 5
            if ([int]$response.StatusCode -eq 401) {
                return $true
            }
        }
        catch {
            if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) {
                return $true
            }
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Mostrar-Log($archivo) {
    if (Test-Path $archivo) {
        Write-Host "`nÚltimos registros de $archivo" -ForegroundColor DarkYellow
        Get-Content $archivo -Tail 15 -ErrorAction SilentlyContinue
    }
}

function Detener-Servicios {
    Write-Host "`nDeteniendo servicios..." -ForegroundColor Yellow

    $pending = @($procesos | Where-Object { $_ -and $_.Id } | ForEach-Object { $_.Id })
    $descendants = @()

    while ($pending.Count -gt 0) {
        $parentIds = @($pending)
        $children = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object {
            $parentIds -contains $_.ParentProcessId
        })
        if ($children.Count -eq 0) {
            break
        }

        $childIds = @($children | ForEach-Object { $_.ProcessId })
        $descendants += $childIds
        $pending = $childIds
    }

    $ownedIds = @($procesos | ForEach-Object { $_.Id }) + @($descendants) | Sort-Object -Unique
    foreach ($processId in $ownedIds) {
        try {
            $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
            if ($process) {
                Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            }
        }
        catch {
        }
    }

    Write-Host "Servicios detenidos." -ForegroundColor Green
}

try {
    Remove-Item $backendLog,$backendErrorLog,$tunnelBackendLog -ErrorAction SilentlyContinue

    Write-Host ""
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "          INICIANDO VENUS" -ForegroundColor Cyan
    Write-Host "====================================" -ForegroundColor Cyan

    # 1. Comprobar dependencias, PostgreSQL y puertos
    Write-Host "`n[1/10] Comprobando dependencias, PostgreSQL y puertos..." -ForegroundColor Cyan
    foreach ($command in @('node', 'npm', 'cloudflared')) {
        if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
            throw "No se encontró la dependencia requerida: $command."
        }
    }

    Confirmar-PuertoDisponible 5000
    Esperar-PostgreSQL 30

    # 2. Iniciar backend
    Write-Host "`n[2/10] Iniciando backend..." -ForegroundColor Cyan

    Iniciar-Backend | Out-Null

    if (-not (Esperar-Puerto 5000 60)) {
        Mostrar-Log $backendLog
        throw "El backend no inició en el puerto 5000."
    }

    Write-Host "Backend iniciado correctamente." -ForegroundColor Green

    $localBackendResponse = Esperar-HTTP "http://127.0.0.1:5000/api/auth/me" 10 "endpoint local del backend"
    if (-not $localBackendResponse -or [int]$localBackendResponse.StatusCode -ne 401) {
        throw "El endpoint local del backend no respondió con 401 sin token."
    }

    # 3. Crear el único túnel, exclusivamente para el backend
    Write-Host "`n[3/10] Creando túnel del backend..." -ForegroundColor Cyan

    Iniciar-Proceso "cloudflared tunnel --url http://127.0.0.1:5000" $venus $tunnelBackendLog | Out-Null

    $urlBackend = Esperar-URL $tunnelBackendLog 90 "backend"

    if (-not $urlBackend) {
        Mostrar-Log $tunnelBackendLog
        throw "Cloudflare no pudo generar la URL del backend."
    }

    $backendResponse = Esperar-HTTP "$urlBackend/api/auth/me" 30 "endpoint público del backend"
    if (-not $backendResponse -or $backendResponse.StatusCode -ne 401) {
        throw "El endpoint público del backend no respondió con 401 sin token."
    }

    Write-Host "Backend público: $urlBackend" -ForegroundColor Green

    # 4. Registrar la URL temporal en KV mediante el Worker
    Write-Host "`n[4/10] Registrando backend en KV..." -ForegroundColor Cyan
    $registrationResponse = Registrar-Backend $WorkerUrl $urlBackend
    if ([int]$registrationResponse.StatusCode -ne 200) {
        throw "El Worker no confirmó el registro del backend."
    }
    Write-Host "Backend registrado correctamente en KV." -ForegroundColor Green

    # 5. Validar propagación, frontend y API a través de la URL permanente
    Write-Host "`n[5/10] Validando URL permanente de VENUS..." -ForegroundColor Cyan
    if (-not (Esperar-WorkerAPI $WorkerUrl 60)) {
        Mostrar-Log $tunnelBackendLog
        throw "El Worker no pudo responder HTTP 401 en /api/auth/me después del registro KV."
    }

    $webResponse = Esperar-HTTP $WorkerUrl 30 "VENUS WEB público"
    if (-not $webResponse -or [int]$webResponse.StatusCode -ne 200) {
        throw "VENUS WEB no respondió con HTTP 200 desde el Worker."
    }

    # 6. Guardar direcciones
    @"
BACKEND=$urlBackend
VENUS_WEB=$WorkerUrl
API=$WorkerUrl
"@ | Set-Content "$venus\URLs_VENUS.txt" -Encoding UTF8

    Write-Host "`n[6/6] Direcciones generadas correctamente." -ForegroundColor Green

    Write-Host ""
    Write-Host "====================================" -ForegroundColor Cyan
    Write-Host "           VENUS EN LINEA" -ForegroundColor Green
    Write-Host "====================================" -ForegroundColor Cyan

    Write-Host "`nBACKEND:" -ForegroundColor Yellow
    Write-Host $urlBackend -ForegroundColor Green

    Write-Host "`nVENUS WEB / API:" -ForegroundColor Yellow
    Write-Host $WorkerUrl -ForegroundColor Green

    Write-Host "`nURLs guardadas en:" -ForegroundColor Cyan
    Write-Host "$venus\URLs_VENUS.txt"

    Write-Host "`nAbre la dirección de VENUS WEB en tu navegador."
    Write-Host "`nPresioná ENTER para detener todos los servicios."
    Read-Host
}
catch {
    Write-Host "`n====================================" -ForegroundColor Red
    Write-Host "          ERROR AL INICIAR VENUS" -ForegroundColor Red
    Write-Host "====================================" -ForegroundColor Red

    Write-Host $_.Exception.Message -ForegroundColor Red

    Detener-Servicios

    Write-Host "`nRevisa los registros de errores en:" -ForegroundColor Yellow
    Write-Host $logs

    Write-Host "`nPresioná ENTER para cerrar."
    Read-Host

    exit 1
}

Detener-Servicios
