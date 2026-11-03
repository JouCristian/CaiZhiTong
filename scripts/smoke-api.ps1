$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'load-env.ps1')
$base = if ($env:SMOKE_API_URL) { $env:SMOKE_API_URL.TrimEnd('/') } else { 'http://127.0.0.1:8080' }
function Check-Status([string]$Path, [int]$Expected, [string]$Token = '') {
    $headers = @{}
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$base$Path" -Headers $headers
        $code = [int]$response.StatusCode
    } catch {
        if (!$_.Exception.Response) { throw }
        $code = [int]$_.Exception.Response.StatusCode
    }
    if ($code -ne $Expected) { throw "$Path expected $Expected, got $code" }
    Write-Host "PASS GET $Path -> $Expected"
}
Check-Status '/actuator/health' 200
Check-Status '/api/auth/me' 401
foreach ($account in @(@{Name='admin'; Password=$env:DEMO_ADMIN_PASSWORD; Admin=$true},@{Name='lab'; Password=$env:DEMO_LAB_PASSWORD; Admin=$false},@{Name='student'; Password=$env:DEMO_STUDENT_PASSWORD; Admin=$false})) {
    $body = @{username=$account.Name; password=$account.Password} | ConvertTo-Json
    $result = Invoke-RestMethod -Uri "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body
    if ($result.code -ne 0 -or !$result.data.accessToken) { throw 'Login failed' }
    Write-Host "PASS real login: $($account.Name)"
    $token = $result.data.accessToken
    Check-Status '/api/auth/me' 200 $token
    Check-Status '/api/workspace/overview' 200 $token
    $expected = if ($account.Admin) { 200 } else { 403 }
    Check-Status '/api/workspace/modules/users' $expected $token
}
Check-Status '/api/auth/me' 401 'invalid.jwt.signature'
Write-Host 'All API smoke checks passed. No tokens or passwords were printed.'

