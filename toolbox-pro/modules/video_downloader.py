"""
模块2：万能视频下载器
基于 yt-dlp，支持 B站/YouTube/抖音等 1000+ 网站
"""

import os
import threading
import customtkinter as ctk
from tkinter import filedialog, scrolledtext, messagebox

try:
    import yt_dlp
    HAS_YTDLP = True
except ImportError:
    HAS_YTDLP = False


class VideoDownloaderFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color="transparent")
        self.save_dir = os.path.join(os.path.expanduser("~"), "Downloads")
        self._build_ui()

    def _build_ui(self):
        title = ctk.CTkLabel(
            self, text="万能视频下载",
            font=ctk.CTkFont(size=24, weight="bold"),
        )
        title.pack(pady=(30, 5), padx=30, anchor="w")

        desc = ctk.CTkLabel(
            self, text="支持B站、YouTube、抖音、快手等 1000+ 网站，自动选择最佳画质",
            font=ctk.CTkFont(size=13), text_color="gray",
        )
        desc.pack(pady=(0, 20), padx=30, anchor="w")

        card = ctk.CTkFrame(self, corner_radius=12)
        card.pack(fill="x", padx=30, pady=5)

        ctk.CTkLabel(card, text="视频链接（每行一个）：", font=ctk.CTkFont(size=14)).pack(pady=(16, 4), padx=20, anchor="w")
        self.url_input = ctk.CTkTextbox(card, height=80, font=ctk.CTkFont(size=13))
        self.url_input.pack(fill="x", padx=20, pady=4)

        opt_row = ctk.CTkFrame(card, fg_color="transparent")
        opt_row.pack(fill="x", padx=20, pady=8)

        ctk.CTkLabel(opt_row, text="画质：").pack(side="left", padx=(0, 4))
        self.quality_var = ctk.CTkOptionMenu(
            opt_row, width=120,
            values=["最佳", "1080p", "720p", "480p", "仅音频MP3"],
        )
        self.quality_var.set("最佳")
        self.quality_var.pack(side="left", padx=4)

        ctk.CTkLabel(opt_row, text="保存到：").pack(side="left", padx=(16, 4))
        self.path_entry = ctk.CTkEntry(opt_row, width=240)
        self.path_entry.insert(0, self.save_dir)
        self.path_entry.pack(side="left", padx=4)
        ctk.CTkButton(opt_row, text="浏览", width=60, command=self._choose_dir).pack(side="left", padx=4)

        btn_row = ctk.CTkFrame(card, fg_color="transparent")
        btn_row.pack(fill="x", padx=20, pady=(4, 16))
        self.btn_download = ctk.CTkButton(
            btn_row, text="开始下载", height=38, width=140,
            font=ctk.CTkFont(size=14, weight="bold"),
            command=self._start_download,
        )
        self.btn_download.pack(side="left")

        self.progress = ctk.CTkProgressBar(btn_row, width=200)
        self.progress.pack(side="left", padx=16)
        self.progress.set(0)

        self.status_label = ctk.CTkLabel(btn_row, text="就绪", text_color="gray", font=ctk.CTkFont(size=12))
        self.status_label.pack(side="left", padx=8)

        self.log = scrolledtext.ScrolledText(card, height=160, font=("Consolas", 11), bg="#1e1e1e", fg="#cccccc")
        self.log.pack(fill="both", expand=True, padx=20, pady=(0, 16))

    def _choose_dir(self):
        d = filedialog.askdirectory(initialdir=self.save_dir)
        if d:
            self.save_dir = d
            self.path_entry.delete(0, "end")
            self.path_entry.insert(0, d)

    def _log(self, msg):
        self.log.insert("end", msg + "\n")
        self.log.see("end")

    def _start_download(self):
        if not HAS_YTDLP:
            messagebox.showerror("错误", "yt-dlp 未安装，请运行 pip install yt-dlp")
            return
        raw = self.url_input.get("1.0", "end").strip()
        if not raw:
            return
        self.urls = [u.strip() for u in raw.split("\n") if u.strip()]
        self.btn_download.configure(state="disabled", text="下载中...")
        self.progress.set(0)
        self.log.delete("1.0", "end")
        threading.Thread(target=self._worker, daemon=True).start()

    def _worker(self):
        quality = self.quality_var.get()
        fmt_map = {
            "最佳": "bestvideo+bestaudio/best",
            "1080p": "bestvideo[height<=1080]+bestaudio/best[height<=1080]",
            "720p": "bestvideo[height<=720]+bestaudio/best[height<=720]",
            "480p": "bestvideo[height<=480]+bestaudio/best[height<=480]",
            "仅音频MP3": "bestaudio/best",
        }
        save_dir = self.path_entry.get().strip()
        total = len(self.urls)
        for i, url in enumerate(self.urls):
            self._log(f"[{i+1}/{total}] 开始下载: {url[:60]}...")
            self.after(0, lambda: self.status_label.configure(text=f"下载 {i+1}/{total}...", text_color="orange"))

            def hook(d):
                if d["status"] == "downloading":
                    total_bytes = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                    downloaded = d.get("downloaded_bytes", 0)
                    if total_bytes > 0:
                        frac = downloaded / total_bytes
                        self.after(0, lambda f=frac: self.progress.set(f))
                elif d["status"] == "finished":
                    self._log(f"  [OK] 文件已保存: {d.get('filename', '')}")

            opts = {
                "format": fmt_map.get(quality, "best"),
                "outtmpl": os.path.join(save_dir, "%(title).80s.%(ext)s"),
                "progress_hooks": [hook],
                "quiet": True,
                "no_warnings": True,
            }
            if quality == "仅音频MP3":
                opts["postprocessors"] = [{
                    "key": "FFmpegExtractAudio",
                    "preferredcodec": "mp3",
                    "preferredquality": "192",
                }]
                opts["outtmpl"] = os.path.join(save_dir, "%(title).80s.%(ext)s")

            try:
                with yt_dlp.YoutubeDL(opts) as ydl:
                    ydl.download([url])
                self._log(f"  [OK] 下载完成")
            except Exception as e:
                self._log(f"  [!] 失败: {e}")
            self.after(0, lambda: self.progress.set((i + 1) / total))

        self._log(f"\n全部完成! 共处理 {total} 个链接")
        self.after(0, lambda: self._finish())

    def _finish(self):
        self.btn_download.configure(state="normal", text="开始下载")
        self.progress.set(1)
        self.status_label.configure(text="✅ 全部完成", text_color="green")
