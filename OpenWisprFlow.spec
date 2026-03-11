# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[('config.py', '.'), ('tray_app.py', '.'), ('settings_ui.py', '.'), ('recorder.py', '.'), ('transcriber.py', '.'), ('window_context.py', '.'), ('llm_corrector.py', '.'), ('text_paster.py', '.')],
    hiddenimports=['win32gui', 'win32api', 'win32con', 'pynput', 'pynput.keyboard', 'pynput.keyboard._win32', 'pynput._util', 'pynput._util.win32', 'sounddevice', '_sounddevice_data', 'pystray', 'pystray._win32', 'PIL', 'PIL.Image', 'PIL.ImageDraw', 'tkinter', 'tkinter.ttk', 'tkinter.scrolledtext', 'tkinter.messagebox'],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='OpenWisprFlow',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon='NONE',
)
