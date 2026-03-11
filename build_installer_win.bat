@echo off
echo ============================================================
echo   Open Wispr Flow - Windows Installer Builder
echo ============================================================
echo.
echo This script will:
echo   1. Build the EXE with PyInstaller
echo   2. Create the installer with Inno Setup
echo.

REM Step 1: Build EXE
echo [1/2] Building EXE with PyInstaller...
echo.

python -m PyInstaller --onefile ^
    --name "OpenWisprFlow" ^
    --console ^
    --hidden-import=win32gui ^
    --hidden-import=win32api ^
    --hidden-import=win32con ^
    --hidden-import=pynput ^
    --hidden-import=pynput.keyboard ^
    --hidden-import=pynput.keyboard._win32 ^
    --hidden-import=pynput._util ^
    --hidden-import=pynput._util.win32 ^
    --hidden-import=sounddevice ^
    --hidden-import=_sounddevice_data ^
    main.py

if not exist "dist\OpenWisprFlow.exe" (
    echo.
    echo ERROR: PyInstaller build failed!
    pause
    exit /b 1
)

echo.
echo [2/2] Creating installer with Inno Setup...
echo.

REM Try common Inno Setup locations
set ISCC_PATH=
if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" (
    set "ISCC_PATH=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
)
if exist "C:\Program Files\Inno Setup 6\ISCC.exe" (
    set "ISCC_PATH=C:\Program Files\Inno Setup 6\ISCC.exe"
)

if "%ISCC_PATH%"=="" (
    echo.
    echo Inno Setup not found! 
    echo.
    echo To create the installer:
    echo   1. Download Inno Setup from https://jrsoftware.org/isdl.php
    echo   2. Install it
    echo   3. Open installer_win.iss in Inno Setup Compiler
    echo   4. Click Build ^> Compile
    echo.
    echo The EXE is ready at: dist\OpenWisprFlow.exe
    echo You can distribute this EXE directly if you prefer.
    pause
    exit /b 0
)

"%ISCC_PATH%" installer_win.iss

if exist "installer_output\OpenWisprFlow_Setup.exe" (
    echo.
    echo ============================================================
    echo   SUCCESS!
    echo   Installer: installer_output\OpenWisprFlow_Setup.exe
    echo ============================================================
) else (
    echo.
    echo Installer build failed. Check errors above.
)

pause
