#!/bin/bash
set -e

echo "============================================================"
echo "  Building Open Wispr Flow - macOS App"
echo "============================================================"
echo

# Check if pyinstaller is installed
if ! python3 -m PyInstaller --version &>/dev/null; then
    echo "Installing PyInstaller..."
    pip3 install pyinstaller
fi

echo
echo "Building macOS app..."
echo

python3 -m PyInstaller \
    --onefile \
    --name "OpenWisprFlow" \
    --noconsole \
    --add-data "config.py:." \
    --add-data "tray_app.py:." \
    --add-data "settings_ui.py:." \
    --add-data "recorder.py:." \
    --add-data "transcriber.py:." \
    --add-data "window_context.py:." \
    --add-data "llm_corrector.py:." \
    --add-data "text_paster.py:." \
    --hidden-import=pynput \
    --hidden-import=pynput.keyboard \
    --hidden-import=pynput.keyboard._darwin \
    --hidden-import=pynput._util \
    --hidden-import=pynput._util.darwin \
    --hidden-import=sounddevice \
    --hidden-import=_sounddevice_data \
    --hidden-import=pystray \
    --hidden-import=pystray._darwin \
    --hidden-import=PIL \
    --hidden-import=PIL.Image \
    --hidden-import=PIL.ImageDraw \
    --hidden-import=tkinter \
    --hidden-import=tkinter.ttk \
    --hidden-import=tkinter.scrolledtext \
    --hidden-import=tkinter.messagebox \
    --noconfirm \
    main.py

echo
if [ -f "dist/OpenWisprFlow" ]; then
    echo "============================================================"
    echo "  ✅ SUCCESS! Binary created at:"
    echo "  dist/OpenWisprFlow"
    echo "============================================================"
    echo
    echo "To run: just double-click, or:"
    echo "  ./dist/OpenWisprFlow"
    echo
    echo "It will appear in the menu bar and ask for your API key."
    echo "Logs: ~/.open_wispr_flow/app.log"
    echo
    echo "⚠️  IMPORTANT: Grant these permissions in System Settings:"
    echo "  • Accessibility (for keyboard listener)"
    echo "  • Microphone (for audio recording)"
    echo "  • Input Monitoring (for global hotkeys)"
    echo
else
    echo "============================================================"
    echo "  ❌ BUILD FAILED - Check errors above"
    echo "============================================================"
fi
