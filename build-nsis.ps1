$ErrorActionPreference = "Stop"

Write-Host "1. Manual Build (Copying Files)..." -ForegroundColor Cyan
# Re-run manual build logic inline to be safe
$distDir = "dist\win-unpacked"
$electronDist = "node_modules\electron\dist"
if (Test-Path $distDir) { Remove-Item -Recurse -Force $distDir }
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
Copy-Item "$electronDist\*" -Destination $distDir -Recurse
$appDir = "$distDir\resources\app"
New-Item -ItemType Directory -Force -Path $appDir | Out-Null
Copy-Item "package.json", "main.js", "renderer.js", "preload.js", "inject.js", "index.html", "settings.html", "styles.css", "darkmode.js", "Yes.png", "Yes.ico", "icon.ico", "logo.png" -Destination $appDir
Copy-Item "node_modules" -Destination $appDir -Recurse
Rename-Item "$distDir\electron.exe" "SnapSeek.exe"

Write-Host "2. Patching Icon (rcedit)..." -ForegroundColor Cyan
# Find rcedit in cache
$rcedit = Get-ChildItem -Path "$env:LOCALAPPDATA\electron-builder\Cache" -Recurse -Filter "rcedit-x64.exe" | Select-Object -First 1 -ExpandProperty FullName

if ($rcedit) {
    Write-Host "Found rcedit at: $rcedit"
    # Execute rcedit to set the icon
    $exePath = "$distDir\SnapSeek.exe"
    $iconPath = "icon.ico"
    
    # rcedit.exe "path/to/exe" --set-icon "path/to/icon.ico"
    & $rcedit $exePath --set-icon $iconPath
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Icon patched successfully!" -ForegroundColor Green
    }
    else {
        Write-Warning "Failed to patch icon."
    }
}
else {
    Write-Warning "rcedit not found. Icon will be default."
}

Write-Host "3. Building NSIS Installer..." -ForegroundColor Cyan
# Create installer from the now-patched folder
npm run build:installer

if ($LASTEXITCODE -eq 0) {
    Write-Host "SUCCESS! Installer created." -ForegroundColor Green
    Get-ChildItem "dist\*.exe" | Select-Object Name, Length, LastWriteTime
}
else {
    Write-Error "NSIS Build Failed."
}
