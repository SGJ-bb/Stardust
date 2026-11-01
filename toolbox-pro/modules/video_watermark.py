"""
模块1：短视频去水印下载器
支持抖音、小红书、快手
"""

import re
import os
import threading
import requests
import customtkinter as ctk
from tkinter import filedialog, scrolledtext

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                  "AppleWebKit/537.36 (KHTML, like Gecko) "
                  "Chrome/120.0.0.0 Safari/537.36",
}


def extract_url(text):
    m = re.search(r'https?://[^\s，,]+', text or "")
    return m.group(0) if m else ""


def resolve_douyin(share_url):
    resp = requests.get(share_url, headers=HEADERS, allow_redirects=False, timeout=10)
    if resp.status_code in (301, 302):
        share_url = resp.headers.get("Location", share_url)
    resp = requests.get(share_url, headers=HEADERS, timeout=10)
    m = re.search(r'video/(\d+)', resp.url)
    if not m:
        m = re.search(r'/(\d+)', resp.url)
    vid = m.group(1) if m else ""
    if not vid:
        return None, "无法提取抖音视频ID"
    api = f"https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id={vid}"
    api_resp = requests.get(api, headers={**HEADERS, "Referer": "https://www.douyin.com/"}, timeout=10)
    data = api_resp.json()
    video = data.get("aweme_detail", {}).get("video", {})
    play_addr = video.get("play_addr", {})
    url_list = play_addr.get("url_list", [])
    if not url_list:
        return None, "未找到视频下载地址"
    video_url = url_list[0].replace("playwm", "play")
    desc = data.get("aweme_detail", {}).get("desc", "douyin_video")
    return video_url, desc


def resolve_kuaishou(share_url):
    resp = requests.get(share_url, headers=HEADERS, allow_redirects=True, timeout=10)
    m = re.search(r'/short-video/([a-zA-Z0-9]+)', resp.url)
    if not m:
        return None, "无法提取快手视频ID"
    vid = m.group(1)
    api = f"https://www.kuaishou.com/graphql"
    gql = {
        "operationName": "visionVideoDetail",
        "variables": {"photoId": vid, "page": "detail"},
        "query": "query visionVideoDetail($photoId: String, $page: String) { visionVideoDetail(photoId: $photoId, page: $page) { photo { duration caption photoUrl } } }"
    }
    api_resp = requests.post(api, json=gql, headers={**HEADERS, "Referer": resp.url}, timeout=10)
    data = api_resp.json()
    photo = data.get("data", {}).get("visionVideoDetail", {}).get("photo", {})
    video_url = photo.get("photoUrl")
    if not video_url:
        return None, "未找到视频下载地址"
    desc = photo.get("caption", "kuaishou_video")
    return video_url, desc


def resolve_xiaohongshu(share_url):
    resp = requests.get(share_url, headers=HEADERS, allow_redirects=True, timeout=10)
    m = re.search(r'/explore/([a-zA-Z0-9]+)', resp.url)
    if not m:
        m = re.search(r'/discovery/item/([a-zA-Z0-9]+)', resp.url)
    if not m:
        return None, "无法提取小红书笔记ID"
    note_id = m.group(1)
    api = f"https://www.xiaohongshu.com/notes/{note_id}"
    api_resp = requests.get(api, headers={**HEADERS, "Referer": "https://www.xiaohongshu.com/"}, timeout=10)
    m2 = re.search(r'"originVideoKey"\s*:\s*"([^"]+)"', api_resp.text)
    if not m2:
        m2 = re.search(r'"url"\s*:\s*"(https://sns-video[^"]+)"', api_resp.text)
    if not m2:
        return None, "未找到视频下载地址（可能是图文笔记）"
    video_url = m2.group(1).replace("\\u002F", "/")
    desc = f"xhs_{note_id}"
    return video_url, desc


def resolve_share_link(share_text):
    url = extract_url(share_text)
    if not url:
        return None, "未检测到链接"
    if "douyin" in url or "iesdouyin" in url:
        return resolve_douyin(url)
    if "kuaishou" in url or "chenzhongtech" in url:
        return resolve_kuaishou(url)
    if "xiaohongshu" in url or "xhslink" in url:
        return resolve_xiaohongshu(url)
    return None, "不支持的平台，目前支持抖音/快手/小红书"


def download_video(url, filepath, progress_cb=None):
    resp = requests.get(url, headers=HEADERS, stream=True, timeout=30)
    total = int(resp.headers.get("content-length", 0))
    downloaded = 0
    with open(filepath, "wb") as f:
        for chunk in resp.iter_content(8192):
            f.write(chunk)
            downloaded += len(chunk)
            if progress_cb and total > 0:
                progress_cb(downloaded / total)
    return filepath


class WatermarkRemoverFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color="transparent")
        self.save_dir = os.path.join(os.path.expanduser("~"), "Downloads")
        self._build_ui()

    def _build_ui(self):
        title = ctk.CTkLabel(
            self, text="短视频去水印下载",
            font=ctk.CTkFont(size=24, weight="bold"),
        )
        title.pack(pady=(30, 5), padx=30, anchor="w")

        desc = ctk.CTkLabel(
            self, text="粘贴抖音/快手/小红书分享链接，自动解析无水印视频并下载",
            font=ctk.CTkFont(size=13), text_color="gray",
        )
        desc.pack(pady=(0, 20), padx=30, anchor="w")

        card = ctk.CTkFrame(self, corner_radius=12)
        card.pack(fill="x", padx=30, pady=5)

        ctk.CTkLabel(card, text="分享链接：", font=ctk.CTkFont(size=14)).pack(pady=(16, 4), padx=20, anchor="w")
        self.url_input = ctk.CTkTextbox(card, height=60, font=ctk.CTkFont(size=13))
        self.url_input.pack(fill="x", padx=20, pady=4)

        row = ctk.CTkFrame(card, fg_color="transparent")
        row.pack(fill="x", padx=20, pady=12)

        ctk.CTkLabel(row, text="保存到：").pack(side="left", padx=(0, 6))
        self.path_entry = ctk.CTkEntry(row, width=350)
        self.path_entry.insert(0, self.save_dir)
        self.path_entry.pack(side="left", padx=4)
        ctk.CTkButton(row, text="浏览", width=60, command=self._choose_dir).pack(side="left", padx=4)

        btn_row = ctk.CTkFrame(card, fg_color="transparent")
        btn_row.pack(fill="x", padx=20, pady=(4, 16))
        self.btn_download = ctk.CTkButton(
            btn_row, text="解析并下载", height=38, width=140,
            font=ctk.CTkFont(size=14, weight="bold"),
            command=self._start_download,
        )
        self.btn_download.pack(side="left")

        self.progress = ctk.CTkProgressBar(btn_row, width=200)
        self.progress.pack(side="left", padx=16)
        self.progress.set(0)

        self.status_label = ctk.CTkLabel(btn_row, text="就绪", text_color="gray", font=ctk.CTkFont(size=12))
        self.status_label.pack(side="left", padx=8)

        self.log = scrolledtext.ScrolledText(card, height=140, font=("Consolas", 11), bg="#1e1e1e", fg="#cccccc")
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
        raw = self.url_input.get("1.0", "end").strip()
        if not raw:
            messagebox.showwarning("提示", "请粘贴分享链接")
            return
        self.btn_download.configure(state="disabled", text="处理中...")
        self.progress.set(0)
        self.status_label.configure(text="解析中...", text_color="orange")
        threading.Thread(target=self._worker, args=(raw,), daemon=True).start()

    def _worker(self, raw):
        try:
            self._log(f"[1/3] 解析链接...")
            video_url, desc = resolve_share_link(raw)
            if not video_url:
                self._log(f"[!] 解析失败: {desc}")
                self.after(0, lambda: self._finish(False, desc))
                return

            safe_desc = re.sub(r'[\\/:*?"<>|]', "_", desc)[:50] or "video"
            filepath = os.path.join(self.save_dir, f"{safe_desc}.mp4")
            self._log(f"[2/3] 视频地址: {video_url[:80]}...")
            self._log(f"[2/3] 保存到: {filepath}")
            self.after(0, lambda: self.status_label.configure(text="下载中...", text_color="orange"))

            def progress_cb(p):
                self.after(0, lambda: self.progress.set(p))

            download_video(video_url, filepath, progress_cb)
            self._log(f"[3/3] 下载完成! 文件大小: {os.path.getsize(filepath) / 1024 / 1024:.1f}MB")
            self.after(0, lambda: self._finish(True, "下载完成"))
        except Exception as e:
            self._log(f"[!] 错误: {e}")
            self.after(0, lambda: self._finish(False, str(e)))

    def _finish(self, success, msg):
        self.btn_download.configure(state="normal", text="解析并下载")
        self.progress.set(1 if success else 0)
        if success:
            self.status_label.configure(text="✅ " + msg, text_color="green")
        else:
            self.status_label.configure(text="❌ " + msg, text_color="red")
