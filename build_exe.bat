@echo off
echo ============================================================
echo   Building Open Wispr Flow - Windows EXE
echo ============================================================
echo.

REM Check if pyinstaller is installed
python -m PyInstaller --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Installing PyInstaller...
    python -m pip install pyinstaller
)

echo.
echo Building EXE (this may take a minute)...
echo.

python -m PyInstaller --onefile ^
    --name "OpenWisprFlow" ^
    --icon=NONE ^
    --noconsole ^
    --add-data "config.py;." ^
    --add-data "tray_app.py;." ^
    --add-data "settings_ui.py;." ^
    --add-data "recorder.py;." ^
    --add-data "transcriber.py;." ^
    --add-data "window_context.py;." ^
    --add-data "llm_corrector.py;." ^
    --add-data "text_paster.py;." ^
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
    --hidden-import=pystray ^
    --hidden-import=pystray._win32 ^
    --hidden-import=PIL ^
    --hidden-import=PIL.Image ^
    --hidden-import=PIL.ImageDraw ^
    --hidden-import=tkinter ^
    --hidden-import=tkinter.ttk ^
    --hidden-import=tkinter.scrolledtext ^
    --hidden-import=tkinter.messagebox ^
    --noconfirm ^
    main.py

echo.
if exist "dist\OpenWisprFlow.exe" (
    echo ============================================================
    echo   SUCCESS! EXE created at:
    echo   dist\OpenWisprFlow.exe
    echo ============================================================
    echo.
    echo To run, just double-click dist\OpenWisprFlow.exe
    echo It will appear in your system tray and ask for your API key.
    echo.
) else (
    echo ============================================================
    echo   BUILD FAILED - Check errors above
    echo ============================================================
)

pause
