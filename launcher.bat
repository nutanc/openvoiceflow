@echo off
REM Open Wispr Flow Launcher
REM Checks for API key and launches the app

if defined OPENAI_API_KEY (
    goto :run
)

REM Check if key exists in user environment
for /f "tokens=2*" %%a in ('reg query "HKCU\Environment" /v OPENAI_API_KEY 2^>nul') do set OPENAI_API_KEY=%%b

if defined OPENAI_API_KEY (
    goto :run
)

REM Prompt for API key on first run
echo ============================================================
echo   Open Wispr Flow - First Run Setup
echo ============================================================
echo.
echo No OpenAI API key found.
echo Get one at: https://platform.openai.com/api-keys
echo.
set /p OPENAI_API_KEY="Enter your API key (sk-...): "

if "%OPENAI_API_KEY%"=="" (
    echo No key entered. Exiting.
    pause
    exit /b 1
)

REM Save to user environment for future runs
setx OPENAI_API_KEY "%OPENAI_API_KEY%" >nul 2>&1
echo.
echo API key saved! You won't need to enter it again.
echo.

:run
start "" "%~dp0OpenWisprFlow.exe"
