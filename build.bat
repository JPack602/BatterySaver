@echo off
setlocal
title CellBoost - Build APK
echo Building CellBoost APK...
echo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
set "CELLBOOST_BUILD_EXIT_CODE=%ERRORLEVEL%"
echo.
if "%CELLBOOST_BUILD_EXIT_CODE%"=="0" (
    echo Build succeeded.
    echo APK: "%~dp0outputs\CellBoost.apk"
) else (
    echo Build failed. See the error messages above.
)
echo.
if /I not "%~1"=="--no-pause" pause
exit /b %CELLBOOST_BUILD_EXIT_CODE%
