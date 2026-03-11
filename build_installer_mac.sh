#!/bin/bash
set -e

echo "============================================================"
echo "  Open Wispr Flow - macOS Installer Builder"
echo "============================================================"
echo
echo "This script will:"
echo "  1. Build the app with PyInstaller (.app bundle)"
echo "  2. Create a DMG installer"
echo

# ── Step 1: Build .app bundle ─────────────────────────────────────
echo "[1/3] Building macOS .app bundle..."
echo

python3 -m PyInstaller \
    --windowed \
    --name "OpenWisprFlow" \
    --osx-bundle-identifier "com.openwispflow.app" \
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

if [ ! -d "dist/OpenWisprFlow.app" ]; then
    echo "❌ PyInstaller build failed!"
    exit 1
fi

echo
echo "✅ .app bundle created at dist/OpenWisprFlow.app"

# ── Step 2: Add permission descriptions to Info.plist ─────────────
echo "[2/3] Configuring permissions and re-signing..."

# NOTE: No wrapper script — the Python app itself handles missing API keys
# by auto-opening the settings window. Using a wrapper script breaks macOS's
# process-to-app-bundle association, preventing the app from appearing in
# System Settings → Privacy & Security.

PLIST="dist/OpenWisprFlow.app/Contents/Info.plist"
if [ -f "$PLIST" ]; then
    # Add macOS permission usage descriptions (required for Privacy & Security visibility)
    /usr/libexec/PlistBuddy -c "Add :NSMicrophoneUsageDescription string 'Open Wispr Flow needs microphone access to record your voice for transcription.'" "$PLIST" 2>/dev/null || true
    /usr/libexec/PlistBuddy -c "Add :NSAppleEventsUsageDescription string 'Open Wispr Flow needs automation access to paste transcribed text.'" "$PLIST" 2>/dev/null || true

    echo "  ✅ Info.plist updated with permission descriptions"
fi

# Re-sign the .app bundle after plist modifications (required for macOS to accept it)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENTITLEMENTS="$SCRIPT_DIR/entitlements.plist"
if [ -f "$ENTITLEMENTS" ]; then
    echo "  Re-signing .app with entitlements..."
    codesign --force --deep --sign - --entitlements "$ENTITLEMENTS" dist/OpenWisprFlow.app
    echo "  ✅ App re-signed with entitlements"
else
    echo "  Re-signing .app (ad-hoc)..."
    codesign --force --deep --sign - dist/OpenWisprFlow.app
    echo "  ✅ App re-signed (ad-hoc, no entitlements file found)"
fi

# ── Step 3: Create DMG ────────────────────────────────────────────
echo "[3/3] Creating DMG installer..."
echo

DMG_NAME="OpenWisprFlow_Installer.dmg"
DMG_DIR="dist/dmg_staging"

# Clean up
rm -rf "$DMG_DIR" "dist/$DMG_NAME"
mkdir -p "$DMG_DIR"

# Copy .app to staging
cp -R dist/OpenWisprFlow.app "$DMG_DIR/"

# Create Applications symlink for drag-and-drop install
ln -s /Applications "$DMG_DIR/Applications"

# Create a README in the DMG
cat > "$DMG_DIR/README.txt" << 'README'
Open Wispr Flow - AI Voice Dictation
=====================================

INSTALL:
  Drag "OpenWisprFlow" to the "Applications" folder.

FIRST RUN:
  1. Open OpenWisprFlow from Applications
  2. Enter your OpenAI API key when prompted
  3. Grant permissions when macOS asks:
     - Accessibility (System Settings > Privacy & Security)
     - Microphone
     - Input Monitoring

USAGE:
  Hold Ctrl+Cmd(Win) → Speak → Release → Text appears at cursor

Get an API key: https://platform.openai.com/api-keys
README

# Create DMG
if command -v create-dmg &> /dev/null; then
    # Pretty DMG with create-dmg (brew install create-dmg)
    create-dmg \
        --volname "Open Wispr Flow" \
        --window-pos 200 120 \
        --window-size 600 400 \
        --icon-size 100 \
        --icon "OpenWisprFlow.app" 150 190 \
        --app-drop-link 450 190 \
        --no-internet-enable \
        "dist/$DMG_NAME" \
        "$DMG_DIR"
else
    # Basic DMG with hdiutil (built into macOS)
    hdiutil create \
        -volname "Open Wispr Flow" \
        -srcfolder "$DMG_DIR" \
        -ov \
        -format UDZO \
        "dist/$DMG_NAME"
fi

# Clean up staging
rm -rf "$DMG_DIR"

echo
if [ -f "dist/$DMG_NAME" ]; then
    echo "============================================================"
    echo "  ✅ SUCCESS!"
    echo "  DMG Installer: dist/$DMG_NAME"
    echo "============================================================"
    echo
    echo "  To install:"
    echo "    1. Open the .dmg"
    echo "    2. Drag OpenWisprFlow to Applications"
    echo "    3. Open from Applications"
    echo "    4. Enter your API key when prompted"
    echo
else
    echo "============================================================"
    echo "  ❌ DMG creation failed. Check errors above."
    echo "  The .app bundle is still at: dist/OpenWisprFlow.app"
    echo "============================================================"
fi
