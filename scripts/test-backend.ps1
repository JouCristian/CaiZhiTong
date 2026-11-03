$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'load-env.ps1')
# Test configuration uses a dedicated database. No dev demo seeder runs in tests.
Push-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'backend')
try {
    & .\mvnw.cmd -B test
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed. See backend/target/surefire-reports.' }
} finally { Pop-Location }

