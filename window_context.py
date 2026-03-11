"""
Window Context Detection - Gets the title of the currently active window.

Windows:  Uses win32gui (pywin32)
Linux:    Falls back to xdotool (for WSL/dev testing)
macOS:    Falls back to AppleScript
"""
import sys
import subprocess


def get_active_window_title() -> str:
    """
    Returns the title of the currently focused window.
    Works on Windows, Linux (X11), and macOS.
    """
    try:
        if sys.platform == "win32":
            return _get_title_windows()
        elif sys.platform == "darwin":
            return _get_title_macos()
        else:
            return _get_title_linux()
    except Exception as e:
        print(f"  ⚠ Could not detect active window: {e}")
        return "Unknown Application"


def _get_title_windows() -> str:
    """Get active window title on Windows using win32gui"""
    import win32gui  # type: ignore
    hwnd = win32gui.GetForegroundWindow()
    title = win32gui.GetWindowText(hwnd)
    return title if title else "Unknown Application"


def _get_title_linux() -> str:
    """Get active window title on Linux using xdotool"""
    result = subprocess.run(
        ["xdotool", "getactivewindow", "getwindowname"],
        capture_output=True, text=True, timeout=2,
    )
    return result.stdout.strip() or "Unknown Application"


def _get_title_macos() -> str:
    """Get active window title on macOS using AppleScript"""
    script = '''
    tell application "System Events"
        set frontApp to name of first application process whose frontmost is true
        try
            tell application process frontApp
                set windowTitle to name of front window
            end tell
            return frontApp & " - " & windowTitle
        on error
            return frontApp
        end try
    end tell
    '''
    result = subprocess.run(
        ["osascript", "-e", script],
        capture_output=True, text=True, timeout=2,
    )
    return result.stdout.strip() or "Unknown Application"
