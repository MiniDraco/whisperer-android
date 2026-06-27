@echo off
REM Download the offline Vosk model into app\src\main\assets\model (gitignored).
REM Run once on a fresh clone before building.
cd /d "%~dp0"
set URL=https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
set DEST=app\src\main\assets\model

echo Downloading Vosk model...
curl -L -o "%TEMP%\vosk-model.zip" "%URL%"
if errorlevel 1 ( echo download failed & pause & exit /b 1 )

echo Unpacking to %DEST% ...
if exist "%DEST%" rmdir /s /q "%DEST%"
mkdir "%DEST%"
powershell -NoProfile -Command "Expand-Archive -Path \"$env:TEMP\vosk-model.zip\" -DestinationPath \"$env:TEMP\voskmodel\" -Force; $inner = Get-ChildItem \"$env:TEMP\voskmodel\" -Directory | Select-Object -First 1; Copy-Item -Path \"$($inner.FullName)\*\" -Destination \"%DEST%\" -Recurse -Force"

echo Done. Model is in %DEST%
pause
