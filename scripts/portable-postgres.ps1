param([ValidateSet('start','stop','test-db','e2e-db')][string]$Action = 'start', [switch]$ResetE2E)
$ErrorActionPreference = 'Stop'
if ($ResetE2E -and $Action -ne 'e2e-db') { throw 'ResetE2E is restricted to the dedicated e2e-db action.' }
. (Join-Path $PSScriptRoot 'load-env.ps1')
$root = Split-Path $PSScriptRoot -Parent
$tools = Join-Path $root '.tools'
$install = Join-Path $tools 'postgres-15.15'
$data = Join-Path $root '.runtime/pgdata'
$log = Join-Path $root '.runtime/postgres.log'
$port = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { '5432' }
$pgctl = Join-Path $install 'bin/pg_ctl.exe'
if ($Action -eq 'stop') {
    if (Test-Path -LiteralPath $pgctl) { & $pgctl -D $data -m fast -w stop; if ($LASTEXITCODE) { throw 'PostgreSQL stop failed' } }
    exit
}
if (!(Test-Path -LiteralPath $pgctl)) {
    New-Item -ItemType Directory -Force -Path $tools,$install | Out-Null
    $base = 'https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64/15.15.0/embedded-postgres-binaries-windows-amd64-15.15.0.jar'
    $jar = Join-Path $tools 'postgres-15.15.jar'
    Write-Host 'Downloading portable PostgreSQL 15.15 from Maven Central...'
    Invoke-WebRequest -UseBasicParsing $base -OutFile $jar
    $expected = (Invoke-WebRequest -UseBasicParsing "$base.sha256").Content.Trim()
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $jar).Hash -ne $expected) { throw 'PostgreSQL checksum mismatch.' }
    & tar.exe -xf $jar -C $install
    if ($LASTEXITCODE) { throw 'Cannot extract PostgreSQL jar.' }
    $archive = Get-ChildItem -LiteralPath $install -Filter '*.txz' | Select-Object -First 1
    if (!$archive) { throw 'PostgreSQL binary archive missing.' }
    & tar.exe -xf $archive.FullName -C $install
    if ($LASTEXITCODE) { throw 'Cannot extract PostgreSQL binaries.' }
}
New-Item -ItemType Directory -Force -Path (Join-Path $root '.runtime') | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    $pwfile = Join-Path $root '.runtime/pg-init-password'
    try {
        [IO.File]::WriteAllText($pwfile, $env:DB_PASSWORD, (New-Object Text.UTF8Encoding $false))
        & (Join-Path $install 'bin/initdb.exe') -D $data -U $env:DB_USERNAME --pwfile=$pwfile --auth=scram-sha-256 --encoding=UTF8 --locale=C
        if ($LASTEXITCODE) { throw 'initdb failed' }
    } finally { if (Test-Path -LiteralPath $pwfile) { Remove-Item -LiteralPath $pwfile } }
}
& $pgctl -D $data status | Out-Null
if ($LASTEXITCODE -ne 0) {
    & $pgctl -D $data -l $log -o "-p $port -h 127.0.0.1" -w start
    if ($LASTEXITCODE) { throw 'PostgreSQL startup failed; see .runtime/postgres.log' }
}
if (!$env:JAVA_HOME) { throw 'Set JAVA_HOME to JDK 17 or later.' }
$driver = Join-Path $tools 'postgresql-42.7.8.jar'
if (!(Test-Path -LiteralPath $driver)) {
    $url = 'https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar'
    Invoke-WebRequest -UseBasicParsing $url -OutFile $driver
}
# Pinned artifact digest. Existing installations can start without network access.
$expected = '2A32A9DCBC42D67A50AD3A0DE5EFD102C8D2BE46720045F2CBD6689F160AB7C7'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $driver).Hash -ne $expected) { throw 'JDBC checksum mismatch.' }
$adminArguments = @($Action)
if ($ResetE2E) { $adminArguments += 'reset-business' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') --class-path $driver (Join-Path $PSScriptRoot 'DatabaseAdmin.java') @adminArguments
if ($LASTEXITCODE) { throw 'Database creation failed.' }

