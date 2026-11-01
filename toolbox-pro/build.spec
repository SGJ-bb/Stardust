# -*- mode: python ; coding: utf-8 -*-
# PyInstaller spec file for ToolBox Pro

import os
import sys
from PyInstaller.utils.hooks import collect_data_files

block_cipher = None

datas = []
# 收集 customtkinter 数据文件
datas += collect_data_files('customtkinter')

# 收集 yt-dlp 的 extractors
datas += collect_data_files('yt_dlp')

hiddenimports = [
    'customtkinter',
    'yt_dlp',
    'PIL',
    'PIL._tkinter_finder',
    'pymupdf',
    'fitz',
    'qrcode',
    'requests',
    'csv',
    're',
    'threading',
    'tkinter',
    'tkinter.scrolledtext',
    'tkinter.filedialog',
    'tkinter.messagebox',
]

a = Analysis(
    ['main.py'],
    pathex=[os.path.abspath('.')],
    binaries=[],
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=['matplotlib', 'numpy', 'scipy', 'pandas', 'PyQt5', 'PyQt6', 'PySide2', 'PySide6'],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='ToolBoxPro',
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
    icon=None,
)
