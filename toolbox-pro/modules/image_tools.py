"""
模块4：图片批量处理
格式转换 / 压缩 / 调整大小 / 加水印
"""

import os
import threading
import customtkinter as ctk
from tkinter import filedialog, messagebox

from PIL import Image, ImageDraw, ImageFont


class ImageFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color="transparent")
        self.input_dir = ""
        self.output_dir = ""
        self._build_ui()

    def _build_ui(self):
        title = ctk.CTkLabel(self, text="图片批量处理", font=ctk.CTkFont(size=24, weight="bold"))
        title.pack(pady=(30, 5), padx=30, anchor="w")
        desc = ctk.CTkLabel(self, text="批量转换格式、压缩体积、调整大小、添加水印", font=ctk.CTkFont(size=13), text_color="gray")
        desc.pack(pady=(0, 20), padx=30, anchor="w")

        card = ctk.CTkFrame(self, corner_radius=12)
        card.pack(fill="x", padx=30, pady=5)

        row_dir = ctk.CTkFrame(card, fg_color="transparent")
        row_dir.pack(fill="x", padx=20, pady=(16, 4))
        ctk.CTkButton(row_dir, text="选择图片文件夹", command=self._choose_input).pack(side="left", padx=4)
        self.input_label = ctk.CTkLabel(row_dir, text="未选择", text_color="gray")
        self.input_label.pack(side="left", padx=8)

        row_out = ctk.CTkFrame(card, fg_color="transparent")
        row_out.pack(fill="x", padx=20, pady=4)
        ctk.CTkButton(row_out, text="保存到", width=120, command=self._choose_output).pack(side="left", padx=4)
        self.output_label = ctk.CTkLabel(row_out, text="未选择（默认在源目录下创建output）", text_color="gray")
        self.output_label.pack(side="left", padx=8)

        row_opt = ctk.CTkFrame(card, fg_color="transparent")
        row_opt.pack(fill="x", padx=20, pady=8)

        ctk.CTkLabel(row_opt, text="转换格式：").pack(side="left", padx=4)
        self.fmt_var = ctk.CTkOptionMenu(row_opt, width=100, values=["保持原格式", "JPEG", "PNG", "WEBP", "BMP"])
        self.fmt_var.set("保持原格式")
        self.fmt_var.pack(side="left", padx=4)

        ctk.CTkLabel(row_opt, text="压缩质量：").pack(side="left", padx=(16, 4))
        self.quality_var = ctk.CTkOptionMenu(row_opt, width=100, values=["不压缩", "高", "中", "低"])
        self.quality_var.set("中")
        self.quality_var.pack(side="left", padx=4)

        row_size = ctk.CTkFrame(card, fg_color="transparent")
        row_size.pack(fill="x", padx=20, pady=4)
        ctk.CTkLabel(row_size, text="调整宽度（像素，0=不变）：").pack(side="left", padx=4)
        self.width_entry = ctk.CTkEntry(row_size, width=80)
        self.width_entry.insert(0, "0")
        self.width_entry.pack(side="left", padx=4)

        row_wm = ctk.CTkFrame(card, fg_color="transparent")
        row_wm.pack(fill="x", padx=20, pady=4)
        ctk.CTkLabel(row_wm, text="水印文字（留空=不加水印）：").pack(side="left", padx=4)
        self.wm_entry = ctk.CTkEntry(row_wm, width=200)
        self.wm_entry.pack(side="left", padx=4)

        row_btn = ctk.CTkFrame(card, fg_color="transparent")
        row_btn.pack(fill="x", padx=20, pady=(8, 16))
        self.btn = ctk.CTkButton(row_btn, text="开始处理", height=38, width=140, font=ctk.CTkFont(size=14, weight="bold"), command=self._start)
        self.btn.pack(side="left")
        self.progress = ctk.CTkProgressBar(row_btn, width=200)
        self.progress.pack(side="left", padx=16)
        self.progress.set(0)
        self.status = ctk.CTkLabel(row_btn, text="就绪", text_color="gray", font=ctk.CTkFont(size=12))
        self.status.pack(side="left", padx=8)

    def _choose_input(self):
        d = filedialog.askdirectory()
        if d:
            self.input_dir = d
            self.input_label.configure(text=d)

    def _choose_output(self):
        d = filedialog.askdirectory()
        if d:
            self.output_dir = d
            self.output_label.configure(text=d)

    def _start(self):
        if not self.input_dir:
            messagebox.showwarning("提示", "请选择图片文件夹")
            return
        self.btn.configure(state="disabled", text="处理中...")
        self.progress.set(0)
        threading.Thread(target=self._worker, daemon=True).start()

    def _worker(self):
        exts = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tiff", ".gif"}
        files = [f for f in os.listdir(self.input_dir) if os.path.splitext(f)[1].lower() in exts]
        if not files:
            self.after(0, lambda: self._done(False, "文件夹中没有图片文件"))
            return

        out_dir = self.output_dir or os.path.join(self.input_dir, "output")
        os.makedirs(out_dir, exist_ok=True)

        fmt = self.fmt_var.get()
        quality_map = {"高": 90, "中": 70, "低": 50}
        quality = quality_map.get(self.quality_var.get(), None)
        target_width = int(self.width_entry.get() or "0")
        wm_text = self.wm_entry.get().strip()

        total = len(files)
        success = 0
        for i, fname in enumerate(files):
            try:
                img = Image.open(os.path.join(self.input_dir, fname)).convert("RGBA")
                if target_width and target_width > 0:
                    ratio = target_width / img.width
                    new_h = int(img.height * ratio)
                    img = img.resize((target_width, new_h), Image.LANCZOS)

                if wm_text:
                    overlay = Image.new("RGBA", img.size, (255, 255, 255, 0))
                    draw = ImageDraw.Draw(overlay)
                    font = None
                    for font_path in ["C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/arial.ttf"]:
                        try:
                            font = ImageFont.truetype(font_path, 36)
                            break
                        except Exception:
                            continue
                    if not font:
                        font = ImageFont.load_default()
                    bbox = draw.textbbox((0, 0), wm_text, font=font)
                    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
                    x = (img.width - tw) // 2
                    y = (img.height - th) // 2
                    draw.text((x, y), wm_text, fill=(255, 255, 255, 80), font=font)
                    img = Image.alpha_composite(img, overlay)

                if fmt == "保持原格式":
                    ext = os.path.splitext(fname)[1].lower()
                    if ext in (".jpg", ".jpeg"):
                        out_img = img.convert("RGB")
                        out_img.save(os.path.join(out_dir, fname), quality=quality or 75)
                    else:
                        img.save(os.path.join(out_dir, fname))
                else:
                    out_name = os.path.splitext(fname)[0] + "." + fmt.lower().replace("jpeg", "jpg")
                    if fmt in ("JPEG", "BMP"):
                        out_img = img.convert("RGB")
                        out_img.save(os.path.join(out_dir, out_name), quality=quality or 75)
                    else:
                        img.save(os.path.join(out_dir, out_name))

                success += 1
            except Exception:
                pass

            frac = (i + 1) / total
            self.after(0, lambda f=frac: self.progress.set(f))
            self.after(0, lambda s=success, t=total: self.status.configure(text=f"{s}/{t} 已处理"))

        self.after(0, lambda: self._done(True, f"处理完成! {success}/{total} 张图片", out_dir))

    def _done(self, success, msg, path=None):
        self.btn.configure(state="normal", text="开始处理")
        self.progress.set(1 if success else 0)
        if success:
            self.status.configure(text="✅ " + msg, text_color="green")
            if path:
                messagebox.showinfo("完成", f"{msg}\n\n保存到: {path}")
        else:
            self.status.configure(text="❌ " + msg, text_color="red")
