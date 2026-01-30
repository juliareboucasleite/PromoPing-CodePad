$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

if (-not $env:JAVA_HOME) {
    Write-Host "JAVA_HOME nao definido. Configure para um JDK 17+ (com jpackage)." -ForegroundColor Yellow
    exit 1
}

$png = "src\main\resources\org\example\nodecode.png"
$ico = "src\main\resources\org\example\nodecode.ico"

if (-not (Test-Path $png)) {
    Write-Host "Icone PNG nao encontrado: $png" -ForegroundColor Yellow
    exit 1
}

New-Item -ItemType Directory -Force -Path "dist" | Out-Null

if (-not (Test-Path $ico)) {
    $icoOut = (Resolve-Path "dist") + "\nodecode.ico"
    $magick = Get-Command magick -ErrorAction SilentlyContinue
    if ($magick) {
        try {
            & $magick.Source $png -define icon:auto-resize=256,128,64,48,32,24,16 $icoOut
            if (Test-Path $icoOut) {
                $ico = "dist\nodecode.ico"
            }
        } catch {
            Write-Host "Falha ao gerar .ico com ImageMagick. Tentando conversao simples..." -ForegroundColor Yellow
        }
    }
    if (-not (Test-Path $ico)) {
        try {
            Add-Type -AssemblyName System.Drawing
            $img = [System.Drawing.Image]::FromFile((Resolve-Path $png))
            $icon = [System.Drawing.Icon]::FromHandle($img.GetHicon())
            $fs = New-Object System.IO.FileStream($icoOut, [System.IO.FileMode]::Create)
            $icon.Save($fs)
            $fs.Close()
            $icon.Dispose()
            $img.Dispose()
            $ico = "dist\nodecode.ico"
        } catch {
            Write-Host "Falha ao converter PNG para ICO. Envie um .ico multi-tamanho (16-256)." -ForegroundColor Yellow
        }
    }
}

Write-Host "Build do projeto..." -ForegroundColor Cyan
& .\mvnw -DskipTests package

Write-Host "Copiando dependencias..." -ForegroundColor Cyan
& .\mvnw -DincludeScope=runtime dependency:copy-dependencies -DoutputDirectory=target\app-libs
& .\mvnw "-DincludeArtifactIds=javafx-controls,javafx-fxml,javafx-graphics,javafx-base" -Dclassifier=win `
    dependency:copy-dependencies -DoutputDirectory=target\javafx

$jarName = "PromoPingPainel-1.0-SNAPSHOT.jar"
$appDir = "target\app"
New-Item -ItemType Directory -Force -Path $appDir | Out-Null
Copy-Item -Force -Path ("target\" + $jarName) -Destination $appDir
Copy-Item -Force -Path "target\app-libs\*" -Destination $appDir

$jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path $jpackage)) {
    Write-Host "jpackage nao encontrado em $jpackage" -ForegroundColor Yellow
    exit 1
}

Write-Host "Gerando instalador exe..." -ForegroundColor Cyan
$dest = "dist\installer"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$iconArg = @()
if (Test-Path $ico) {
    $iconArg = @("--icon", (Resolve-Path $ico))
}

& $jpackage `
    --type exe `
    --dest $dest `
    --input $appDir `
    --name "PromoPingCodePad" `
    --app-version "1.0.0" `
    --main-jar $jarName `
    --main-class "org.example.Main" `
    --module-path "target\javafx" `
    --add-modules "javafx.controls,javafx.fxml,javafx.graphics,javafx.base" `
    --win-menu --win-shortcut --win-dir-chooser `
    --win-menu-group "PromoPingCodePad" `
    --install-dir "PromoPingCodePad" `
    --win-per-user-install `
    @iconArg

Write-Host "Pronto. Instalador em dist\installer" -ForegroundColor Green
