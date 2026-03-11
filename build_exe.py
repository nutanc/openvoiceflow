"""
Build script - Alternative to build_exe.bat, works cross-platform.
Run: python build_exe.py
"""
import subprocess
import sys
import os


def main():
    # Ensure PyInstaller is installed
    try:
        import PyInstaller  # noqa: F401
    except ImportError:
        print("Installing PyInstaller...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pyinstaller"])

    # Build command
    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--name", "OpenWisprFlow",
        "--console",  # Keep console for debug output; use --noconsole for silent
        "--add-data", f"config.py{os.pathsep}.",
        # Hidden imports for Windows
        "--hidden-import=win32gui",
        "--hidden-import=win32api",
        "--hidden-import=win32con",
        "--hidden-import=pynput",
        "--hidden-import=pynput.keyboard",
        "--hidden-import=pynput.keyboard._win32",
        "--hidden-import=pynput._util",
        "--hidden-import=pynput._util.win32",
        "--hidden-import=sounddevice",
        "--hidden-import=_sounddevice_data",
        "main.py",
    ]

    print("Building EXE...")
    print(f"Command: {' '.join(cmd)}")
    print()

    result = subprocess.run(cmd)

    if result.returncode == 0:
        exe_path = os.path.join("dist", "OpenWisprFlow.exe")
        print()
        print("=" * 60)
        print(f"  ✅ SUCCESS! EXE: {exe_path}")
        print("=" * 60)
    else:
        print()
        print("❌ Build failed!")
        sys.exit(1)


if __name__ == "__main__":
    main()
