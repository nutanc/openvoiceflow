# -*- mode: python ; coding: utf-8 -*-


a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[('config.py', '.'), ('tray_app.py', '.'), ('settings_ui.py', '.'), ('recorder.py', '.'), ('transcriber.py', '.'), ('window_context.py', '.'), ('llm_corrector.py', '.'), ('text_paster.py', '.')],
    hiddenimports=['pynput', 'pynput.keyboard', 'pynput.keyboard._darwin', 'pynput._util', 'pynput._util.darwin', 'sounddevice', '_sounddevice_data', 'pystray', 'pystray._darwin', 'PIL', 'PIL.Image', 'PIL.ImageDraw', 'tkinter', 'tkinter.ttk', 'tkinter.scrolledtext', 'tkinter.messagebox'],
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
    [],
    exclude_binaries=True,
    name='OpenWisprFlow',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='OpenWisprFlow',
)
app = BUNDLE(
    coll,
    name='OpenWisprFlow.app',
    icon=None,
    bundle_identifier='com.openwispflow.app',
)
