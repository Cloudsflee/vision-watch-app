@echo off
setlocal

set "ROOT_DIR=%~dp0"
set "APK_PATH=%ROOT_DIR%app\build\outputs\apk\debug\app-debug.apk"
set "GRADLE_USER_HOME=%ROOT_DIR%.gradle-user-home"

if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"

echo [1/2] Building debug APK...
call "%ROOT_DIR%gradlew.bat" assembleDebug
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo [2/2] Build finished.
if exist "%APK_PATH%" (
  echo APK generated at:
  echo %APK_PATH%
) else (
  echo Build succeeded but APK file was not found at expected path:
  echo %APK_PATH%
  exit /b 2
)

endlocal
