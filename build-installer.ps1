$ErrorActionPreference = "Stop"

Write-Host "1. Building Icon..." -ForegroundColor Cyan
npm run create-icon
if ($LASTEXITCODE -ne 0) { Write-Error "Icon creation failed." }

Write-Host "2. Building Application Binaries..." -ForegroundColor Cyan

# Attempt 1: Electron Builder
$useManualFallback = $false
try {
    $env:ELECTRON_BUILDER_BINARIES_MIRROR = "https://npmmirror.com/mirrors/electron-builder-binaries/"
    npx electron-builder --dir --config.win.target=dir -c.win.forceCodeSigning=false
    if ($LASTEXITCODE -ne 0) { throw "Builder failed" }
}
catch {
    Write-Warning "Electron Builder failed (likely network issues)."
    Write-Host "Attempting MANUAL BUILD fallback..." -ForegroundColor Yellow
    $useManualFallback = $true
}

if ($useManualFallback) {
    # Manual Build Steps
    $distDir = "dist\win-unpacked"
    $electronDist = "node_modules\electron\dist"
    
    if (-not (Test-Path $electronDist)) {
        Write-Error "Cannot find Electron binaries in node_modules. Run npm install."
    }

    # Clean
    if (Test-Path $distDir) { Remove-Item -Recurse -Force $distDir }
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null

    # Copy Electron
    Copy-Item "$electronDist\*" -Destination $distDir -Recurse

    # Copy App
    $appDir = "$distDir\resources\app"
    New-Item -ItemType Directory -Force -Path $appDir | Out-Null

    # whitelist specific files to copy
    Copy-Item "package.json", "main.js", "renderer.js", "preload.js", "inject.js", "index.html", "settings.html", "styles.css", "darkmode.js", "Yes.png", "Yes.ico", "icon.ico", "logo.png" -Destination $appDir
    
    # Copy node_modules (production only would be ideal but we copy all for safety here)
    # Exclude electron itself to save space/time if possible, but recursive copy is simpler
    # We must skip node_modules/electron to avoid recursion loop if we were in root, but we are copying TO dist
    Copy-Item "node_modules" -Destination $appDir -Recurse

    # Rename Executable
    Rename-Item "$distDir\electron.exe" "SnapSeek.exe"
    
    Write-Host "Manual Build Completed." -ForegroundColor Green
}

Write-Host "3. Compiling Installer (Inno Setup)..." -ForegroundColor Cyan
$isccPathX86 = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
$isccPathX64 = "C:\Program Files\Inno Setup 6\ISCC.exe"
$compiler = $null

if (Test-Path $isccPathX86) { $compiler = $isccPathX86 } 
elseif (Test-Path $isccPathX64) { $compiler = $isccPathX64 }

if ($null -eq $compiler) {
    Write-Error "Inno Setup Compiler not found."
    exit 1
}

& $compiler "setup.iss"

if ($LASTEXITCODE -eq 0) {
    Write-Host "SUCCESS! Installer created at dist-installer/SnapSeek_Setup.exe" -ForegroundColor Green
}
else {
    Write-Error "Installer compilation failed."
}
