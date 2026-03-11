#!/usr/bin/env python3
"""
Open Wispr Flow (Cloud Edition) - AI-Powered Voice Dictation
Uses OpenAI Whisper API + GPT for context-aware transcription and correction.

Runs as a system tray app on Windows/macOS.
Hold Ctrl+Win to record → Release to process → Text pasted at cursor.

Usage:
    python main.py              # System tray mode (default)
    python main.py --cli        # Terminal-only mode (no tray icon)
    python main.py --interactive  # Interactive testing mode
"""
import sys
import os
import logging
from datetime import datetime

# Add script directory to path for PyInstaller bundled exe
if getattr(sys, 'frozen', False):
    os.chdir(os.path.dirname(sys.executable))
    sys.path.insert(0, os.path.dirname(sys.executable))


def setup_file_logging():
    """Redirect stdout/stderr to a log file when running without a console.
    Log file: ~/.open_wispr_flow/app.log (rotated, keeps last 1MB)
    """
    log_dir = os.path.join(os.path.expanduser("~"), ".open_wispr_flow")
    os.makedirs(log_dir, exist_ok=True)
    log_path = os.path.join(log_dir, "app.log")

    # Truncate if log gets too big (>1MB)
    try:
        if os.path.exists(log_path) and os.path.getsize(log_path) > 1_000_000:
            # Keep last 100KB
            with open(log_path, "r", errors="replace") as f:
                f.seek(-100_000, 2)
                tail = f.read()
            with open(log_path, "w") as f:
                f.write("--- Log truncated ---\n")
                f.write(tail)
    except Exception:
        pass

    log_file = open(log_path, "a", encoding="utf-8", buffering=1)  # line-buffered
    log_file.write(f"\n{'='*60}\n")
    log_file.write(f"  Open Wispr Flow started at {datetime.now().isoformat()}\n")
    log_file.write(f"{'='*60}\n")

    sys.stdout = log_file
    sys.stderr = log_file


def run_tray():
    """Run as system tray app (default)"""
    from tray_app import TrayApp
    app = TrayApp()
    app.run()


def run_cli():
    """Run as terminal-only app (original behavior)"""
    from config import load_config
    import config

    load_config()

    if not config.OPENAI_API_KEY:
        print("=" * 60)
        print("❌ ERROR: OPENAI_API_KEY not set!")
        print()
        print("Set it as an environment variable:")
        print('  Windows CMD:   set OPENAI_API_KEY=sk-...')
        print('  Windows PS:    $env:OPENAI_API_KEY="sk-..."')
        print('  Linux/Mac:     export OPENAI_API_KEY=sk-...')
        print()
        print("Or run without --cli to get the GUI settings window.")
        print("=" * 60)
        sys.exit(1)

    from recorder import AudioRecorder
    from transcriber import Transcriber
    from llm_corrector import LLMCorrector
    from window_context import get_active_window_title
    from text_paster import paste_text
    from pynput import keyboard
    import threading

    print()
    print("╔══════════════════════════════════════════════════════╗")
    print("║     Open Wispr Flow (Cloud Edition) — CLI Mode      ║")
    print("╚══════════════════════════════════════════════════════╝")
    print()

    recorder = AudioRecorder()
    transcriber = Transcriber()
    corrector = LLMCorrector()
    is_processing = False
    recording_active = False

    def process():
        nonlocal is_processing
        is_processing = True
        try:
            wav_bytes = recorder.stop()
            if not wav_bytes:
                print("  ❌ No audio captured"); return
            window_title = get_active_window_title()
            print(f"  🪟 Active window: {window_title}")
            raw_text = transcriber.transcribe(wav_bytes)
            if not raw_text:
                print("  ❌ No speech detected"); return
            corrected = corrector.correct(raw_text, window_title)
            paste_text(corrected)
            print("  ─────────────────────────────────────\n")
        except Exception as e:
            print(f"  ❌ Error: {e}")
        finally:
            is_processing = False

    ctrl = win = hotkey = False

    def on_press(key):
        nonlocal ctrl, win, hotkey, recording_active
        try:
            if key in (keyboard.Key.ctrl_l, keyboard.Key.ctrl_r): ctrl = True
            elif key in (keyboard.Key.cmd_l, keyboard.Key.cmd_r, keyboard.Key.cmd):
                win = True
                if ctrl and not hotkey and not is_processing:
                    hotkey = True
                    recording_active = True
                    recorder.start()
        except AttributeError:
            pass

    def on_release(key):
        nonlocal ctrl, win, hotkey, recording_active
        try:
            if key in (keyboard.Key.ctrl_l, keyboard.Key.ctrl_r):
                ctrl = False
                if hotkey:
                    hotkey = False
                    if recording_active:
                        recording_active = False
                        threading.Thread(target=process, daemon=True).start()
            elif key in (keyboard.Key.cmd_l, keyboard.Key.cmd_r, keyboard.Key.cmd):
                win = False
                if hotkey:
                    hotkey = False
                    if recording_active:
                        recording_active = False
                        threading.Thread(target=process, daemon=True).start()
        except AttributeError:
            pass

    print("  ⌨️  Hold Ctrl+Win to record, release to paste")
    print("  ❌ Press Ctrl+C to quit\n")

    with keyboard.Listener(on_press=on_press, on_release=on_release) as listener:
        try:
            listener.join()
        except KeyboardInterrupt:
            pass
    print("\n  👋 Goodbye!")


def run_interactive():
    """Run in interactive testing mode"""
    from config import load_config
    import config

    load_config()

    if not config.OPENAI_API_KEY:
        print("❌ Set OPENAI_API_KEY first, or run without --interactive for GUI.")
        sys.exit(1)

    from recorder import AudioRecorder
    from transcriber import Transcriber
    from llm_corrector import LLMCorrector
    from window_context import get_active_window_title
    from text_paster import paste_text

    recorder = AudioRecorder()
    transcriber = Transcriber()
    corrector = LLMCorrector()

    print("\n  🎤 Interactive mode — press ENTER to record, ENTER to stop\n")

    while True:
        user_input = input("  Press ENTER to record (or 'quit'): ").strip()
        if user_input.lower() == "quit":
            break
        recorder.start()
        input("  Press ENTER to stop recording...")
        wav_bytes = recorder.stop()
        if not wav_bytes:
            print("  ❌ No audio"); continue
        window_title = get_active_window_title()
        raw = transcriber.transcribe(wav_bytes)
        if not raw:
            print("  ❌ No speech"); continue
        corrected = corrector.correct(raw, window_title)
        paste_text(corrected)
        print()

    print("\n  👋 Goodbye!")


def main():
    if "--cli" in sys.argv:
        run_cli()
    elif "--interactive" in sys.argv:
        run_interactive()
    else:
        # In tray mode with no console, redirect output to log file
        if getattr(sys, 'frozen', False):
            setup_file_logging()
        run_tray()


if __name__ == "__main__":
    import multiprocessing
    multiprocessing.freeze_support()
    main()
