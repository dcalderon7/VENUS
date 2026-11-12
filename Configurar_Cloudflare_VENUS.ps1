$ErrorActionPreference = 'Stop'

$venus = 'C:\Users\AMD\Downloads\Proyectos Android\Venus'
$web = 'C:\Users\AMD\Downloads\Proyectos Android\VenusWeb'
$worker = "$venus\cloudflare-worker"
$localConfigPath = "$venus\Venus.cloudflare.local.ps1"
$wranglerConfigPath = "$worker\wrangler.jsonc"
$productionEnvPath = "$web\.env.production"

function Fail($message) {
    throw $message
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Fail 'Node.js no estÃ¡ instalado o no estÃ¡ en PATH.'
}
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Fail 'npm no estÃ¡ instalado o no estÃ¡ en PATH.'
}
if (-not (Test-Path "$worker\wrangler.jsonc")) {
    Fail "No existe la configuraciÃ³n de Wrangler: $wranglerConfigPath"
}

Write-Host 'Comprobando autenticaciÃ³n de Cloudflare...' -ForegroundColor Cyan
$whoamiOutput = & npx.cmd --yes wrangler whoami 2>&1
$needsLogin = $LASTEXITCODE -ne 0 -or (($whoamiOutput -join [Environment]::NewLine) -match 'not authenticated|not logged in|no autenticado')
if ($needsLogin) {
    Write-Host 'Se abrirÃ¡ el navegador para autenticar Wrangler.' -ForegroundColor Yellow
    & npx.cmd --yes wrangler login
    if ($LASTEXITCODE -ne 0) {
        Fail 'La autenticaciÃ³n de Wrangler no se completÃ³.'
    }
}

$configText = Get-Content $wranglerConfigPath -Raw
$kvMatch = [regex]::Match($configText, '"id"\s*:\s*"([^"]+)"')
$kvId = if ($kvMatch.Success) { $kvMatch.Groups[1].Value } else { '' }

if ([string]::IsNullOrWhiteSpace($kvId) -or $kvId -eq 'REPLACE_WITH_WRANGLER_KV_ID') {
    Write-Host 'Creando namespace KV VENUS_CONFIG...' -ForegroundColor Cyan
    $kvOutput = & npx.cmd --yes wrangler kv namespace create VENUS_CONFIG 2>&1
    if ($LASTEXITCODE -ne 0) {
        Fail "No se pudo crear el namespace KV: $($kvOutput -join [Environment]::NewLine)"
    }

    $jsonText = ($kvOutput -join [Environment]::NewLine)
    $idMatch = [regex]::Match($jsonText, '"id"\s*:\s*"([^"]+)"')
    if (-not $idMatch.Success) {
        Fail 'Wrangler no devolviÃ³ el ID del namespace KV.'
    }
    $kvId = $idMatch.Groups[1].Value
    if ([string]::IsNullOrWhiteSpace($kvId)) {
        Fail 'Wrangler no devolviÃ³ un ID vÃ¡lido para VENUS_CONFIG.'
    }

    $configText = $configText -replace 'REPLACE_WITH_WRANGLER_KV_ID', $kvId
    Set-Content -Path $wranglerConfigPath -Value $configText -Encoding UTF8
}

$local = $null
if (Test-Path $localConfigPath) {
    . $localConfigPath
}

$adminSecret = if ($AdminRegistrationSecret) { [string]$AdminRegistrationSecret } else { '' }
if ([string]::IsNullOrWhiteSpace($adminSecret)) {
    $secureSecret = Read-Host 'Secreto administrativo del Worker (no se mostrarÃ¡)' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureSecret)
    try {
        $adminSecret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}
if ($adminSecret.Length -lt 32) {
    Fail 'El secreto administrativo debe tener al menos 32 caracteres.'
}

Write-Host 'Publicando Worker inicial...' -ForegroundColor Cyan
$deployOutput = & npx.cmd --yes wrangler deploy --config $wranglerConfigPath 2>&1
if ($LASTEXITCODE -ne 0) {
    Fail "El despliegue inicial fallÃ³: $($deployOutput -join [Environment]::NewLine)"
}

$deployText = $deployOutput -join [Environment]::NewLine
$workerMatch = [regex]::Match($deployText, 'https://(?:[a-z0-9-]+\.)+workers\.dev', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
$workerUrl = if ($WorkerUrl) { ([string]$WorkerUrl).TrimEnd('/') } elseif ($workerMatch.Success) { $workerMatch.Value.TrimEnd('/') } else { '' }
if ([string]::IsNullOrWhiteSpace($workerUrl)) {
    Fail 'No se pudo obtener la URL workers.dev real de Wrangler.'
}

Write-Host 'Configurando secreto administrativo del Worker...' -ForegroundColor Cyan
$adminSecret | & npx.cmd --yes wrangler secret put ADMIN_REGISTRATION_SECRET --config $wranglerConfigPath
if ($LASTEXITCODE -ne 0) {
    Fail 'No se pudo configurar ADMIN_REGISTRATION_SECRET.'
}

Set-Content -Path $productionEnvPath -Value "VITE_API_URL=$workerUrl" -Encoding UTF8
Write-Host 'Construyendo VenusWeb...' -ForegroundColor Cyan
Push-Location $web
try {
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) {
        Fail 'El build de VenusWeb fallÃ³.'
    }
}
finally {
    Pop-Location
}

Write-Host 'Publicando assets y Worker final...' -ForegroundColor Cyan
& npx.cmd --yes wrangler deploy --config $wranglerConfigPath
if ($LASTEXITCODE -ne 0) {
    Fail 'El despliegue final del Worker fallÃ³.'
}

$escapedWorkerUrl = $workerUrl.Replace("'", "''")
$escapedSecret = $adminSecret.Replace("'", "''")
@"
`$WorkerUrl = '$escapedWorkerUrl'
`$AdminRegistrationSecret = '$escapedSecret'
"@ | Set-Content -Path $localConfigPath -Encoding UTF8

Write-Host ''
Write-Host 'ConfiguraciÃ³n completada.' -ForegroundColor Green
Write-Host "Worker: $workerUrl" -ForegroundColor Green
Write-Host "KV: VENUS_CONFIG ($kvId)" -ForegroundColor Green
Write-Host "ConfiguraciÃ³n local: $localConfigPath" -ForegroundColor Green

