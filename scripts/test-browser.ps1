param([switch]$OAuthMock,[Parameter(ValueFromRemainingArguments=$true)][string[]]$TestArguments)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'load-env.ps1')
foreach ($port in $(if($OAuthMock){@(8081,5180,9098)}else{@(8081,5180)})) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "E2E port $port is occupied. No existing process will be stopped."
    }
}
# Exercise a fresh production bundle and avoid native dev-transform crashes in
# the Windows Node/Vite combination. This does not publish or deploy anything.
Push-Location (Join-Path $root 'frontend')
$originalApiBase = $env:VITE_API_BASE_URL
try {
    $env:VITE_API_BASE_URL = ''
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'E2E production build failed.' }
} finally { $env:VITE_API_BASE_URL = $originalApiBase; Pop-Location }
& (Join-Path $PSScriptRoot 'portable-postgres.ps1') e2e-db -ResetE2E
$runtime = Join-Path $root '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$backend = $null
$frontend = $null
$provider = $null
$originalOAuth = $env:E2E_OAUTH
$originalBase = $env:E2E_BASE_URL
$originalIsolation = $env:E2E_ISOLATED
$runnerShell = if (Get-Command pwsh.exe -ErrorAction SilentlyContinue) { (Get-Command pwsh.exe).Source } else { 'powershell.exe' }
function Wait-Ready([string]$Url, [System.Diagnostics.Process]$Process) {
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) { throw "E2E server exited; inspect .runtime/e2e-*.log" }
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Url; inspect .runtime/e2e-*.log"
}
try {
    $backendScript = Join-Path $PSScriptRoot 'start-backend.ps1'
    $frontendScript = Join-Path $PSScriptRoot 'start-e2e-frontend.ps1'
    if($OAuthMock){
        $provider=Start-Process (Get-Command node.exe).Source -WindowStyle Hidden -PassThru -ArgumentList @((Join-Path $PSScriptRoot 'mock-github.mjs')) -RedirectStandardOutput (Join-Path $runtime 'mock-github.out.log') -RedirectStandardError (Join-Path $runtime 'mock-github.err.log')
        Wait-Ready 'http://127.0.0.1:9098/health' $provider
    }
    $backendArgs=@('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $backendScript + '"'),'-E2E')
    if($OAuthMock){$backendArgs+='-OAuthMock'}
    $backend = Start-Process $runnerShell -WindowStyle Hidden -PassThru -ArgumentList $backendArgs -RedirectStandardOutput (Join-Path $runtime 'e2e-backend.out.log') -RedirectStandardError (Join-Path $runtime 'e2e-backend.err.log')
    $frontend = Start-Process $runnerShell -WindowStyle Hidden -PassThru -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $frontendScript + '"')) -RedirectStandardOutput (Join-Path $runtime 'e2e-frontend.out.log') -RedirectStandardError (Join-Path $runtime 'e2e-frontend.err.log')
    Wait-Ready 'http://127.0.0.1:8081/actuator/health' $backend
    Wait-Ready 'http://127.0.0.1:5180/login' $frontend
    $env:E2E_BASE_URL = 'http://127.0.0.1:5180'
    $env:E2E_ISOLATED = '1'
    $env:E2E_OAUTH=if($OAuthMock){'1'}else{'0'}
    if($OAuthMock){
        $verificationPassword=ConvertTo-SecureString $env:DEMO_ADMIN_PASSWORD -AsPlainText -Force
        & (Join-Path $PSScriptRoot 'verify-public-demo.ps1') -BaseUrl 'http://127.0.0.1:8081' -Password $verificationPassword
    }
    if(!$OAuthMock){
    & (Join-Path $PSScriptRoot 'smoke-materials.ps1')
    & (Join-Path $PSScriptRoot 'smoke-stock.ps1')
    & (Join-Path $PSScriptRoot 'smoke-issues.ps1')
    & (Join-Path $PSScriptRoot 'smoke-lifecycle.ps1')
    & (Join-Path $PSScriptRoot 'smoke-administration.ps1')
    }
    Write-Host 'E2E uses caizhitong_v070_demo_e2e_test, backend 8081, frontend 5180. User data is untouched.'
    Push-Location (Join-Path $root 'frontend')
    try {
        & npm.cmd test -- @TestArguments
        if ($LASTEXITCODE -ne 0) { throw 'Browser tests failed; see frontend/playwright-report.' }
    } finally { Pop-Location }
} finally {
    foreach ($ownedProcess in @($frontend,$backend,$provider)) {
        if ($ownedProcess -and !$ownedProcess.HasExited) {
            & taskkill.exe /PID $ownedProcess.Id /T /F 2>$null | Out-Null
        }
    }
    $env:E2E_BASE_URL = $originalBase
    $env:E2E_ISOLATED = $originalIsolation
    $env:E2E_OAUTH = $originalOAuth
}
