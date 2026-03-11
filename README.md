# Open Wispr Flow — Cloud Edition

**AI-powered voice dictation for Windows & macOS** — speak naturally and get clean, context-aware text pasted at your cursor. An open source wisprflow.ai alternative

## How It Works

```
Hold Ctrl+CMD(Win) → Speak → Release → Text appears at cursor
```

1. **Record** — Captures audio while you hold the hotkey
2. **Transcribe** — Sends audio to OpenAI Whisper API
3. **Detect Context** — Reads the title of your active window (VS Code, Chrome, Slack, etc.)
4. **Correct** — GPT fixes transcription errors using the window context
5. **Paste** — Clean text is pasted at your cursor position

You can install the executables from the dist folder. Set the api key and press ctrl followed by CMD9Win) key and start speaking. You can edit settings in the tray application and update the promt as you see fit.

## Quick Start

### Prerequisites

- **Python 3.9+** — [Download](https://www.python.org/downloads/)
- **OpenAI API Key** — [Get one here](https://platform.openai.com/api-keys)

---

### Windows

```powershell
cd open_wispr_flow\cloud
pip install -r requirements.txt
$env:OPENAI_API_KEY = "sk-your-key-here"
python main.py
```

### macOS

```bash
cd open_wispr_flow/cloud
pip3 install -r requirements_mac.txt
export OPENAI_API_KEY=sk-your-key-here
python3 main.py
```

> ⚠️ **macOS Permissions Required**: Go to **System Settings → Privacy & Security** and grant:
> - **Accessibility** — for global hotkey listener
> - **Microphone** — for audio recording
> - **Input Monitoring** — for keyboard events

---

### Interactive Mode (for testing)

```bash
python main.py --interactive
```

## Building a Standalone App

### Windows → EXE (standalone)

```cmd
build_exe.bat
```

### macOS → Binary (standalone)

```bash
chmod +x build_mac.sh && ./build_mac.sh
```

---

## Creating Installers

### Windows Installer (.exe setup wizard)

Creates a proper installer with Start Menu shortcut, Desktop icon, optional auto-start, and API key prompt during install.

**Prerequisites**: Install [Inno Setup 6](https://jrsoftware.org/isdl.php) (free)

```cmd
REM One-click build (builds EXE + installer)
build_installer_win.bat
```

Or step by step:
1. Build the EXE: `build_exe.bat`
2. Open `installer_win.iss` in Inno Setup Compiler
3. Click **Build → Compile**
4. Installer at: `installer_output\OpenWisprFlow_Setup.exe`

Launch sequence is ctrl+Win. Hold and speak and release to get transcription in the open textarea


### macOS Installer (.dmg with drag-to-Applications)

Creates a `.app` bundle with native API key dialog on first launch, packaged in a `.dmg`.

```bash
chmod +x build_installer_mac.sh
./build_installer_mac.sh
```

Output: `dist/OpenWisprFlow_Installer.dmg`

User installs by:
1. Open the `.dmg`
2. Drag **OpenWisprFlow** to **Applications**
3. Make sure you give input tools, microphone and accessibility permissions in privacy settings after installing.
4. Launch from Applications
5. Enter API key when prompted (saved for future runs)

Launch sequence is ctrl+cmd. Hold and speak and release to get transcription in the open textarea

## Configuration

Settings are stored at `~/.open_wispr_flow/config.json`. You can also edit `config.py` directly before building.

| Setting | Default | Options |
|---|---|---|
| `GPT_MODEL` | `gpt-4o-mini` | `gpt-4o`, `gpt-4o-mini`, `gpt-3.5-turbo` |
| `WHISPER_MODEL` | `whisper-1` | `whisper-1` |
| `RECORD_MODE` | `push_to_talk` | `push_to_talk`, `toggle` |
| `HOTKEY` | `Ctrl+Win` | Configurable in `config.py` |

## Project Structure

```
cloud/
├── main.py              # Entry point + hotkey listener
├── config.py            # Configuration
├── recorder.py          # Audio recording (sounddevice)
├── transcriber.py       # OpenAI Whisper API
├── window_context.py    # Active window detection (cross-platform)
├── llm_corrector.py     # OpenAI GPT correction
├── text_paster.py       # Clipboard + paste simulation
├── build_exe.bat        # Windows EXE build script
├── build_exe.py         # Python EXE build script
├── build_mac.sh         # macOS build script
├── requirements.txt     # Windows dependencies
├── requirements_mac.txt # macOS dependencies
└── README.md            # This file
```

## Costs

| Component | Cost |
|---|---|
| Whisper (30s audio) | ~$0.006 |
| GPT-4o-mini correction | ~$0.0003 |
| **Total per dictation** | **~$0.007** |

That's roughly **140 dictations per $1**.

## Troubleshooting

| Issue | Platform | Fix |
|---|---|---|
| `OPENAI_API_KEY not set` | Both | Set the environment variable before running |
| `No audio captured` | Windows | Check microphone in Sound Settings |
| `No audio captured` | macOS | Grant Microphone permission in System Settings |
| `win32gui not found` | Windows | `pip install pywin32` |
| Hotkey not working | macOS | Grant Accessibility & Input Monitoring permissions |
| EXE/binary won't start | Both | Run from terminal to see error messages |
| Paste not working | Both | Make sure a text field has focus before releasing hotkey |

