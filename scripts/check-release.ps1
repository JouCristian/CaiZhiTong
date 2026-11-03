$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    foreach ($path in @('.env','.env.cloud','frontend/.env','.runtime/pgdata/PG_VERSION','backend/.runtime/uploads/photo.jpg','uploads/photo.jpg','private.key','certificate.pfx')) {
        & git check-ignore --quiet -- $path
        if ($LASTEXITCODE -ne 0) { throw "Sensitive path is not ignored: $path" }
    }
    $privateValues = @()
    foreach ($privateFile in @('.env','.env.cloud','frontend/.env')) {
      if (Test-Path -LiteralPath $privateFile) {
        foreach ($line in Get-Content -LiteralPath $privateFile) {
            if ($line -match '^[A-Z][A-Z0-9_]*(PASSWORD|SECRET|TOKEN|CREDENTIAL|_KEY)=.+$') {
                $value = $line.Substring($line.IndexOf('=') + 1)
                if ($value.Length -ge 10 -and !$value.StartsWith('REPLACE_')) { $privateValues += $value }
            }
        }
      }
    }
    $files = @(& git -c core.quotepath=false ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate release candidate files.' }
    $extensions = @('.java','.yml','.yaml','.ts','.tsx','.json','.md','.sql','.ps1','.mjs','.txt','.properties','.example','.html','.css','.xml','.cmd','.py')
    $issues = @()
    $scanned = 0
    foreach ($file in $files) {
        if (![IO.File]::Exists((Join-Path $root $file))) { continue }
        if ($extensions -notcontains [IO.Path]::GetExtension($file)) { continue }
        $body = [IO.File]::ReadAllText((Join-Path $root $file), [Text.Encoding]::UTF8)
        $scanned++
        if ($body -match '(?m)^-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----') { $issues += $file; continue }
        foreach ($secret in $privateValues) { if ($body.Contains($secret)) { $issues += $file; break } }
    }
    if ($issues.Count) { throw ('Private material found in release candidates (values suppressed): ' + (($issues | Sort-Object -Unique) -join ', ')) }
    Write-Host "PASS: sensitive paths ignored; $scanned text candidates checked for local secrets and private key headers. No secret values printed."
} finally { Pop-Location }
