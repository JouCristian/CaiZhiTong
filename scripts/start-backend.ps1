param([switch]$E2E)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'load-env.ps1')
if ($E2E) {
    $port = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { '5432' }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$port/caizhitong_e2e_test"
    $env:SERVER_PORT = '8081'
    $env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:5180'
    $env:PUBLIC_WEB_URL = 'http://127.0.0.1:5180'
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:DEMO_ENABLED = 'true'
    $env:MAIL_HOST = ''
    $env:MAIL_FROM = ''
    $env:MAIL_RECIPIENTS = ''
    $env:MAIL_DISPATCH_ENABLED = 'false'
    $env:STORAGE_ROOT = Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime/e2e-uploads'
}
Push-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'backend')
try {
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See console output.' }
} finally { Pop-Location }

