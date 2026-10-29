param([switch]$Install)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-home'
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.android'
New-Item -ItemType Directory -Force -Path (Join-Path $projectRoot 'work'), (Join-Path $projectRoot 'outputs') | Out-Null
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') :app:assembleDebug :app:lintDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android build or lint failed.' }
    $apk = Join-Path $projectRoot 'outputs\BatterySaver.apk'
    Copy-Item -LiteralPath (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk') -Destination $apk -Force
    Write-Host "APK: $apk"
    if ($Install) {
        $sdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        $adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
        & $adbPath install -r $apk
        if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Check the selected device.' }
        & $adbPath shell am start -n jp.battery.saver/.MainActivity
        if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
    }
} finally {
    Pop-Location
}