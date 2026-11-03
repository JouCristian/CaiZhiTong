$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $root '.env'
if (Test-Path -LiteralPath $envFile) { Write-Host '.env already exists; preserving all values.'; exit 0 }
function Random-Secret([int]$length = 32) {
    $bytes = New-Object byte[] $length
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}
$content = Get-Content -LiteralPath (Join-Path $root '.env.example') -Raw
$content = $content.Replace('REPLACE_WITH_RANDOM_VALUE', (Random-Secret))
$content = $content.Replace('REPLACE_WITH_BASE64_OF_AT_LEAST_32_RANDOM_BYTES', (Random-Secret 48))
foreach ($key in @('DEMO_ADMIN_PASSWORD','DEMO_LAB_PASSWORD','DEMO_STUDENT_PASSWORD')) {
    $content = $content -replace "$key=REPLACE_WITH_LOCAL_PASSWORD", "$key=$(Random-Secret 18)"
}
[IO.File]::WriteAllText($envFile, $content, (New-Object Text.UTF8Encoding $false))
Write-Host 'Created ignored .env with random database/JWT/demo credentials.'
Write-Host 'Read DEMO_*_PASSWORD values in .env for local login. Do not commit this file.'

