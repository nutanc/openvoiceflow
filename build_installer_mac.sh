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

# ── Step 2: Create launcher script inside .app ────────────────────
echo "[2/3] Adding API key launcher..."

# Create a wrapper script that checks for API key
cat > dist/OpenWisprFlow.app/Contents/MacOS/launch_wrapper.sh << 'WRAPPER'
#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="$HOME/.open_wispr_flow/config.json"

# Check for API key
if [ -z "$OPENAI_API_KEY" ]; then
    # Try to load from config
    if [ -f "$CONFIG_FILE" ]; then
        KEY=$(python3 -c "import json; print(json.load(open('$CONFIG_FILE')).get('OPENAI_API_KEY',''))" 2>/dev/null)
        if [ -n "$KEY" ]; then
            export OPENAI_API_KEY="$KEY"
        fi
    fi
fi

if [ -z "$OPENAI_API_KEY" ]; then
    # Prompt with osascript dialog
    KEY=$(osascript -e 'display dialog "Enter your OpenAI API key:" & return & return & "Get one at: https://platform.openai.com/api-keys" default answer "" with title "Open Wispr Flow Setup" buttons {"Cancel", "Save"} default button "Save"' -e 'text returned of result' 2>/dev/null)
    
    if [ -z "$KEY" ]; then
        osascript -e 'display alert "No API key provided" message "Open Wispr Flow needs an OpenAI API key to work." as critical'
        exit 1
    fi
    
    export OPENAI_API_KEY="$KEY"
    
    # Save for future runs
    mkdir -p "$HOME/.open_wispr_flow"
    echo "{\"OPENAI_API_KEY\": \"$KEY\"}" > "$CONFIG_FILE"
fi

exec "$SCRIPT_DIR/OpenWisprFlow"
WRAPPER

chmod +x dist/OpenWisprFlow.app/Contents/MacOS/launch_wrapper.sh

# Update Info.plist to use wrapper
PLIST="dist/OpenWisprFlow.app/Contents/Info.plist"
if [ -f "$PLIST" ]; then
    # Replace CFBundleExecutable to use the wrapper
    sed -i '' 's|<string>OpenWisprFlow</string>|<string>launch_wrapper.sh</string>|' "$PLIST" 2>/dev/null || \
    sed -i 's|<string>OpenWisprFlow</string>|<string>launch_wrapper.sh</string>|' "$PLIST"
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
