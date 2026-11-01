"""
ToolBox Pro - 全能工具箱
主窗口 + 侧边栏导航
"""

import customtkinter as ctk
import os
import sys
from tkinter import messagebox

from modules.video_watermark import WatermarkRemoverFrame
from modules.video_downloader import VideoDownloaderFrame
from modules.pdf_tools import PDFFrame
from modules.image_tools import ImageFrame
from modules.file_renamer import FileRenamerFrame
from modules.qr_generator import QRGeneratorFrame

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")


class ToolBoxPro(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title("ToolBox Pro - 全能工具箱")
        self.geometry("900x620")
        self.minsize(800, 560)
        self._build_sidebar()
        self._build_content()
        self.show_frame("watermark")

    def _build_sidebar(self):
        self.sidebar = ctk.CTkFrame(self, width=200, corner_radius=0)
        self.sidebar.pack(side="left", fill="y", padx=0, pady=0)
        self.sidebar.pack_propagate(False)

        logo = ctk.CTkLabel(
            self.sidebar, text="\U0001f9f0 ToolBox Pro",
            font=ctk.CTkFont(size=20, weight="bold"),
        )
        logo.pack(pady=(24, 4), padx=20, anchor="w")

        sub = ctk.CTkLabel(
            self.sidebar, text="全能工具箱 v1.0",
            font=ctk.CTkFont(size=12), text_color="gray",
        )
        sub.pack(pady=(0, 20), padx=20, anchor="w")

        self.nav_buttons = {}
        nav_items = [
            ("watermark", "\U0001f3a8 短视频去水印"),
            ("downloader", "\U0001f4be 万能视频下载"),
            ("pdf", "\U0001f4c4 PDF工具箱"),
            ("image", "\U0001f5bc\ufe0f 图片批量处理"),
            ("renamer", "\U0001f4c1 文件批量重命名"),
            ("qrcode", "\U0001f4f2 二维码生成器"),
        ]
        for key, label in nav_items:
            btn = ctk.CTkButton(
                self.sidebar, text=label,
                corner_radius=8, height=40,
                anchor="w", fg_color="transparent",
                text_color=("gray10", "gray90"),
                hover_color=("gray70", "gray30"),
                command=lambda k=key: self.show_frame(k),
            )
            btn.pack(pady=3, padx=12, fill="x")
            self.nav_buttons[key] = btn

        footer = ctk.CTkLabel(
            self.sidebar, text="永久授权版",
            font=ctk.CTkFont(size=11), text_color="gray",
        )
        footer.pack(side="bottom", pady=16)

    def _build_content(self):
        self.content = ctk.CTkFrame(self, corner_radius=0)
        self.content.pack(side="right", fill="both", expand=True, padx=0, pady=0)

        self.frames = {}
        self.frames["watermark"] = WatermarkRemoverFrame(self.content, self)
        self.frames["downloader"] = VideoDownloaderFrame(self.content, self)
        self.frames["pdf"] = PDFFrame(self.content, self)
        self.frames["image"] = ImageFrame(self.content, self)
        self.frames["renamer"] = FileRenamerFrame(self.content, self)
        self.frames["qrcode"] = QRGeneratorFrame(self.content, self)

        for frame in self.frames.values():
            frame.pack(fill="both", expand=True)

    def show_frame(self, key):
        for f in self.frames.values():
            f.pack_forget()
        self.frames[key].pack(fill="both", expand=True)
        for k, btn in self.nav_buttons.items():
            if k == key:
                btn.configure(fg_color=("#3b8ed0", "#1f6aa5"))
            else:
                btn.configure(fg_color="transparent")


def resource_path(relative):
    if hasattr(sys, "_MEIPASS"):
        return os.path.join(sys._MEIPASS, relative)
    return os.path.join(os.path.abspath("."), relative)


if __name__ == "__main__":
    app = ToolBoxPro()
    app.mainloop()
