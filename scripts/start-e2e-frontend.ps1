$ErrorActionPreference = 'Stop'
$env:API_PROXY_TARGET = 'http://127.0.0.1:8081'
# CI mode avoids closing on inherited stdin. This cannot prevent a native
# Node runtime crash; use a maintained Node patch release and inspect exit codes.
$env:CI = 'true'
Push-Location (Join-Path (Split-Path $PSScriptRoot -Parent) 'frontend')
try {
    & node.exe (Join-Path $PSScriptRoot 'e2e-frontend.mjs')
    if ($LASTEXITCODE -ne 0) { throw "E2E frontend exited with code $LASTEXITCODE. See .runtime/e2e-frontend.*.log" }
} finally { Pop-Location }
