"""
Text Paster - Pastes corrected text at the cursor position.

Uses clipboard + simulated Ctrl+V, which works universally across all apps.
"""
import time
import sys
import subprocess
import pyperclip


def paste_text(text: str):
    """
    Copy text to clipboard and simulate Ctrl+V to paste at cursor.
    
    Works on Windows, Linux, and macOS.
    """
    if not text:
        return

    # Save current clipboard
    try:
        original_clipboard = pyperclip.paste()
    except Exception:
        original_clipboard = None

    # Copy corrected text to clipboard
    pyperclip.copy(text)

    # Small delay to ensure clipboard is ready
    time.sleep(0.05)

    # Simulate Ctrl+V
    _simulate_paste()

    # Small delay for paste to complete
    time.sleep(0.1)

    # Optionally restore original clipboard (uncomment if desired)
    # if original_clipboard is not None:
    #     time.sleep(0.3)
    #     pyperclip.copy(original_clipboard)

    print("  📋 Text pasted at cursor!")


def _simulate_paste():
    """Simulate paste keystroke (Ctrl+V on Windows/Linux, Cmd+V on macOS)"""
    if sys.platform == "win32":
        _paste_windows()
    elif sys.platform == "darwin":
        _paste_macos()
    else:
        _paste_pynput()


def _paste_windows():
    """Paste on Windows using win32 API for reliability"""
    try:
        import win32api  # type: ignore
        import win32con  # type: ignore
        # Simulate Ctrl+V using keybd_event
        win32api.keybd_event(win32con.VK_CONTROL, 0, 0, 0)
        win32api.keybd_event(ord('V'), 0, 0, 0)
        win32api.keybd_event(ord('V'), 0, win32con.KEYEVENTF_KEYUP, 0)
        win32api.keybd_event(win32con.VK_CONTROL, 0, win32con.KEYEVENTF_KEYUP, 0)
    except ImportError:
        # Fallback to pynput
        _paste_pynput()


def _paste_macos():
    """Paste on macOS using Cmd+V via AppleScript (no Accessibility permission needed)"""
    try:
        subprocess.run(
            ["osascript", "-e",
             'tell application "System Events" to keystroke "v" using command down'],
            timeout=3, check=True,
            capture_output=True,
        )
    except Exception as e:
        print(f"  ⚠ AppleScript paste failed ({e}), trying pynput fallback...")
        from pynput.keyboard import Controller, Key
        kb = Controller()
        with kb.pressed(Key.cmd):
            kb.press('v')
            kb.release('v')


def _paste_pynput():
    """Paste using pynput (Linux fallback, uses Ctrl+V)"""
    from pynput.keyboard import Controller, Key
    kb = Controller()
    with kb.pressed(Key.ctrl):
        kb.press('v')
        kb.release('v')
