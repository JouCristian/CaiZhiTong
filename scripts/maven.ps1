$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Set JAVA_HOME to JDK 17 or later.'
}
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { $bin = $maven.Source }
else {
    $cached = Get-ChildItem (Join-Path $env:USERPROFILE '.m2/wrapper/dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cached) { $bin = $cached.FullName }
    else {
        $version = '3.9.16'
        $tools = Join-Path $root '.tools'
        New-Item -ItemType Directory -Force -Path $tools | Out-Null
        $zip = Join-Path $tools "apache-maven-$version-bin.zip"
        $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
        if (!(Test-Path -LiteralPath $zip)) { Invoke-WebRequest -UseBasicParsing $url -OutFile $zip }
        $expected = (Invoke-WebRequest -UseBasicParsing "$url.sha512").Content.Trim().Split(' ')[0]
        if ((Get-FileHash -Algorithm SHA512 -LiteralPath $zip).Hash -ne $expected) { throw 'Maven checksum mismatch.' }
        $dir = Join-Path $tools "apache-maven-$version"
        if (!(Test-Path -LiteralPath $dir)) { Expand-Archive -LiteralPath $zip -DestinationPath $tools }
        $bin = Join-Path $dir 'bin/mvn.cmd'
    }
}
& $bin @args
exit $LASTEXITCODE

