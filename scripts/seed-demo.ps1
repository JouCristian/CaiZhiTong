param([switch]$E2E)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'load-env.ps1')
if ($env:SPRING_PROFILES_ACTIVE -ne 'dev' -or $env:DEMO_ENABLED -ne 'true') { throw 'Demo data is only available in the explicitly enabled dev environment.' }
if ($E2E) {
    $port = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { '5432' }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:$port/caizhitong_v070_demo_e2e_test"
}
$root = Split-Path $PSScriptRoot -Parent
$driver = Join-Path $root '.tools/postgresql-42.7.8.jar'
if (!(Test-Path -LiteralPath $driver)) { throw 'Run portable-postgres.ps1 start first.' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') --class-path $driver (Join-Path $PSScriptRoot 'SqlRunner.java') (Join-Path $root 'database/demo-materials.sql')
if ($LASTEXITCODE -ne 0) { throw 'Demo SQL failed. Start the backend once to apply V2 first.' }
Write-Host 'Demo materials are stored in PostgreSQL. No stock or batches were created.'

