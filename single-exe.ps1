$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME nao definido. Configure para um JDK 21+." -ForegroundColor Yellow
    exit 1
}

$sevenZip = "C:\Program Files\7-Zip\7z.exe"
$sfx = "C:\Program Files\7-Zip\7z.sfx"
if (-not (Test-Path $sevenZip) -or -not (Test-Path $sfx)) {
    Write-Host "7-Zip nao encontrado em C:\Program Files\7-Zip. Instale o 7-Zip." -ForegroundColor Yellow
    exit 1
}

Write-Host "Gerando app-image..." -ForegroundColor Cyan
& .\package.ps1

$appDir = "dist\PromoPingCodePad"
$outExe = "dist\PromoPingCodePad-Standalone.exe"
$temp7z = "dist\PromoPingCodePad.7z"
$config = "dist\7zconfig.txt"

if (-not (Test-Path $appDir)) {
    Write-Host "App-image nao encontrado em $appDir" -ForegroundColor Yellow
    exit 1
}

";!@Install@!UTF-8!`nRunProgram=`"PromoPingCodePad.exe`"`n;!@InstallEnd@!" | Set-Content -Path $config -Encoding ASCII

Write-Host "Compactando..." -ForegroundColor Cyan
& $sevenZip a -t7z -mx=9 $temp7z "$appDir\*"

Write-Host "Gerando exe unico..." -ForegroundColor Cyan
$bytes = [System.IO.File]::ReadAllBytes($sfx) + [System.IO.File]::ReadAllBytes($config) + [System.IO.File]::ReadAllBytes($temp7z)
[System.IO.File]::WriteAllBytes($outExe, $bytes)

Write-Host "Pronto. Arquivo: $outExe" -ForegroundColor Green
