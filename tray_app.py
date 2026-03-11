"""
System Tray App - Runs Open Wispr Flow in the Windows/macOS system tray
with right-click menu for Settings, Status, and Quit.
"""
import sys
import os
import threading
import time

# Add script directory to path for PyInstaller
if getattr(sys, 'frozen', False):
    os.chdir(os.path.dirname(sys.executable))
    sys.path.insert(0, os.path.dirname(sys.executable))

import config
from config import load_config, save_config

# ── Icon Generation ────────────────────────────────────────────────
def _create_icon_image():
    """Create a simple microphone icon for the tray"""
    from PIL import Image, ImageDraw
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # Circle background
    draw.ellipse([2, 2, size - 2, size - 2], fill="#89b4fa")
    # Microphone shape (simple rectangle + stand)
    cx = size // 2
    draw.rounded_rectangle([cx - 7, 12, cx + 7, 34], radius=5, fill="white")
    draw.arc([cx - 12, 22, cx + 12, 42], start=0, end=180, fill="white", width=2)
    draw.line([cx, 42, cx, 50], fill="white", width=2)
    draw.line([cx - 8, 50, cx + 8, 50], fill="white", width=2)
    return img


def _create_recording_icon():
    """Create a red recording icon"""
    from PIL import Image, ImageDraw
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([2, 2, size - 2, size - 2], fill="#f38ba8")
    cx = size // 2
    draw.rounded_rectangle([cx - 7, 12, cx + 7, 34], radius=5, fill="white")
    draw.arc([cx - 12, 22, cx + 12, 42], start=0, end=180, fill="white", width=2)
    draw.line([cx, 42, cx, 50], fill="white", width=2)
    draw.line([cx - 8, 50, cx + 8, 50], fill="white", width=2)
    return img


def _run_settings_process(current_config):
    """Run settings UI in a separate process so Tk gets its own main thread.
    This is required on macOS where NSWindow must be created on the main thread.
    """
    from settings_ui import SettingsWindow
    from config import save_config

    def on_save(new_cfg):
        save_config(new_cfg)

    win = SettingsWindow(on_save_callback=on_save)
    win.show(current_config)


class TrayApp:
    """System tray application for Open Wispr Flow"""

    def __init__(self):
        self.flow = None
        self.tray_icon = None
        self.keyboard_listener = None
        self._status = "Waiting for API key..."
        self._running = True
        self._normal_icon = _create_icon_image()
        self._recording_icon = _create_recording_icon()

        # Load config
        load_config()

    @property
    def is_configured(self):
        return bool(config.OPENAI_API_KEY)

    def _get_current_config(self):
        """Get current config as dict for settings UI"""
        return {
            "OPENAI_API_KEY": config.OPENAI_API_KEY,
            "GPT_MODEL": config.GPT_MODEL,
            "WHISPER_MODEL": config.WHISPER_MODEL,
            "WHISPER_LANGUAGE": config.WHISPER_LANGUAGE,
            "GPT_TEMPERATURE": config.GPT_TEMPERATURE,
            "RECORD_MODE": config.RECORD_MODE,
            "SYSTEM_PROMPT": config.SYSTEM_PROMPT,
        }

    def _on_settings_save(self, new_config: dict):
        """Called when settings are saved from the UI"""
        # Save to file
        save_config(new_config)

        # Update in-memory config
        for key, value in new_config.items():
            if hasattr(config, key):
                setattr(config, key, value)

        # Reinitialize the flow engine if API key changed
        if self.flow is None and new_config.get("OPENAI_API_KEY"):
            self._init_flow_engine()

    def _init_flow_engine(self):
        """Initialize the recording/transcription engine"""
        try:
            from recorder import AudioRecorder
            from transcriber import Transcriber
            from llm_corrector import LLMCorrector

            self.recorder = AudioRecorder()
            self.transcriber = Transcriber()
            self.corrector = LLMCorrector()
            self.flow = True
            self._is_processing = False
            self._recording_active = False
            self._status = "Ready — Hold Ctrl+Win to dictate"
            self._update_tray_title()
            print("  ✅ Engine initialized!")
        except Exception as e:
            self._status = f"Error: {e}"
            print(f"  ❌ Init error: {e}")

    def _update_tray_title(self):
        """Update tray icon tooltip"""
        if self.tray_icon:
            self.tray_icon.title = f"Open Wispr Flow — {self._status}"

    def _set_recording_icon(self, recording: bool):
        """Switch between normal and recording icons"""
        if self.tray_icon:
            self.tray_icon.icon = self._recording_icon if recording else self._normal_icon

    def _process_recording(self):
        """Full pipeline: stop recording → transcribe → correct → paste"""
        self._is_processing = True
        self._status = "Processing..."
        self._update_tray_title()
        try:
            from window_context import get_active_window_title
            from text_paster import paste_text

            wav_bytes = self.recorder.stop()
            self._set_recording_icon(False)

            if not wav_bytes:
                print("  ❌ No audio captured")
                self._status = "Ready — Hold Ctrl+Win to dictate"
                self._update_tray_title()
                return

            window_title = get_active_window_title()
            print(f"  🪟 Active window: {window_title}")

            raw_text = self.transcriber.transcribe(wav_bytes)
            if not raw_text:
                print("  ❌ No speech detected")
                self._status = "Ready — Hold Ctrl+Win to dictate"
                self._update_tray_title()
                return

            corrected_text = self.corrector.correct(raw_text, window_title)
            paste_text(corrected_text)

            self._status = "Ready — Hold Ctrl+Win to dictate"
            self._update_tray_title()
            print("  ─────────────────────────────────────")

        except Exception as e:
            print(f"  ❌ Error: {e}")
            import traceback
            traceback.print_exc()
            self._status = f"Error: {e}"
            self._update_tray_title()
        finally:
            self._is_processing = False

    def _on_hotkey_press(self):
        if not self.flow or self._is_processing:
            return
        if config.RECORD_MODE == "toggle":
            if self._recording_active:
                self._recording_active = False
                self._set_recording_icon(False)
                threading.Thread(target=self._process_recording, daemon=True).start()
            else:
                self._recording_active = True
                self._set_recording_icon(True)
                self._status = "🎙️ Recording..."
                self._update_tray_title()
                self.recorder.start()
        else:
            self._recording_active = True
            self._set_recording_icon(True)
            self._status = "🎙️ Recording..."
            self._update_tray_title()
            self.recorder.start()

    def _on_hotkey_release(self):
        if config.RECORD_MODE != "push_to_talk":
            return
        if not self._recording_active:
            return
        self._recording_active = False
        threading.Thread(target=self._process_recording, daemon=True).start()

    def _start_keyboard_listener(self):
        """Start global hotkey listener"""
        from pynput import keyboard

        ctrl_pressed = False
        win_pressed = False
        hotkey_active = False

        def on_press(key):
            nonlocal ctrl_pressed, win_pressed, hotkey_active
            try:
                if key in (keyboard.Key.ctrl_l, keyboard.Key.ctrl_r):
                    ctrl_pressed = True
                elif key in (keyboard.Key.cmd_l, keyboard.Key.cmd_r, keyboard.Key.cmd):
                    win_pressed = True
                    if ctrl_pressed and not hotkey_active:
                        hotkey_active = True
                        self._on_hotkey_press()
                elif ctrl_pressed and win_pressed and not hotkey_active:
                    hotkey_active = True
                    self._on_hotkey_press()
            except AttributeError:
                pass

        def on_release(key):
            nonlocal ctrl_pressed, win_pressed, hotkey_active
            try:
                if key in (keyboard.Key.ctrl_l, keyboard.Key.ctrl_r):
                    ctrl_pressed = False
                    if hotkey_active:
                        hotkey_active = False
                        self._on_hotkey_release()
                elif key in (keyboard.Key.cmd_l, keyboard.Key.cmd_r, keyboard.Key.cmd):
                    win_pressed = False
                    if hotkey_active:
                        hotkey_active = False
                        self._on_hotkey_release()
            except AttributeError:
                pass

        self.keyboard_listener = keyboard.Listener(
            on_press=on_press, on_release=on_release
        )
        self.keyboard_listener.start()

    def _open_settings(self, icon=None, item=None):
        """Open settings window as a separate process (Tk requires main thread on macOS)"""
        import multiprocessing
        current = self._get_current_config()
        p = multiprocessing.Process(
            target=_run_settings_process,
            args=(current,),
            daemon=True,
        )
        p.start()

        # Reload config after settings window closes (in background)
        def _wait_and_reload():
            p.join()
            self._reload()
        threading.Thread(target=_wait_and_reload, daemon=True).start()

    def _quit(self, icon=None, item=None):
        """Exit the application"""
        self._running = False
        if self.keyboard_listener:
            self.keyboard_listener.stop()
        if self.tray_icon:
            self.tray_icon.stop()

    def run(self):
        """Start the system tray app"""
        import pystray
        from pystray import MenuItem as Item

        print()
        print("╔══════════════════════════════════════════════════════╗")
        print("║     Open Wispr Flow (Cloud Edition)                 ║")
        print("║     Running in system tray                          ║")
        print("╚══════════════════════════════════════════════════════╝")
        print()

        # Initialize engine if API key is available
        if self.is_configured:
            self._init_flow_engine()
        else:
            print("  ⚠️  No API key configured. Opening settings...")
            self._status = "⚠️ API key needed — right-click tray icon → Settings"

        # Start keyboard listener
        self._start_keyboard_listener()

        # Build tray menu
        menu = pystray.Menu(
            Item("Open Wispr Flow", None, enabled=False),
            pystray.Menu.SEPARATOR,
            Item("⚙️  Settings", self._open_settings),
            Item("🔄  Reload Config", lambda icon, item: self._reload()),
            pystray.Menu.SEPARATOR,
            Item("❌  Quit", self._quit),
        )

        self.tray_icon = pystray.Icon(
            name="OpenWisprFlow",
            icon=self._normal_icon,
            title=f"Open Wispr Flow — {self._status}",
            menu=menu,
        )

        # Open settings automatically if no API key
        if not self.is_configured:
            threading.Timer(1.0, self._open_settings).start()

        print("  🔵 System tray icon active")
        print("  💡 Right-click the tray icon for options")
        print()

        self.tray_icon.run()

    def _reload(self):
        """Reload config from file"""
        load_config()
        if self.is_configured and not self.flow:
            self._init_flow_engine()
        print("  🔄 Config reloaded")
