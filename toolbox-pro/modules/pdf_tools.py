"""
模块3：PDF工具箱
合并 / 拆分 / 压缩 / 加水印
"""

import os
import io
import threading
import customtkinter as ctk
from tkinter import filedialog, scrolledtext

try:
    import pymupdf as fitz
    HAS_PYMUPDF = True
except ImportError:
    try:
        import fitz
        HAS_PYMUPDF = True
    except ImportError:
        try:
            from PyPDF2 import PdfReader, PdfWriter
            HAS_PYMUPDF = False
            HAS_PYPDF2 = True
        except ImportError:
            HAS_PYMUPDF = False
            HAS_PYPDF2 = False


class PDFFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color="transparent")
        self._build_ui()

    def _build_ui(self):
        title = ctk.CTkLabel(
            self, text="PDF工具箱",
            font=ctk.CTkFont(size=24, weight="bold"),
        )
        title.pack(pady=(30, 5), padx=30, anchor="w")

        desc = ctk.CTkLabel(
            self, text="合并、拆分、压缩、加水印，一键处理 PDF 文件",
            font=ctk.CTkFont(size=13), text_color="gray",
        )
        desc.pack(pady=(0, 20), padx=30, anchor="w")

        self.tabs = ctk.CTkTabview(self, width=800)
        self.tabs.pack(fill="both", expand=True, padx=30, pady=5)

        self._build_merge_tab(self.tabs.add("合并"))
        self._build_split_tab(self.tabs.add("拆分"))
        self._build_compress_tab(self.tabs.add("压缩"))
        self._build_watermark_tab(self.tabs.add("水印"))

    def _build_merge_tab(self, tab):
        ctk.CTkLabel(tab, text="选择多个PDF文件，按顺序合并为一个文件", text_color="gray").pack(pady=(8, 12))
        self.merge_files = []

        btn_row = ctk.CTkFrame(tab, fg_color="transparent")
        btn_row.pack(fill="x", padx=12)
        ctk.CTkButton(btn_row, text="添加PDF文件", command=self._add_merge_files).pack(side="left", padx=4)
        ctk.CTkButton(btn_row, text="清空", width=80, fg_color="gray", command=self._clear_merge_files).pack(side="left", padx=4)

        self.merge_list = ctk.CTkTextbox(tab, height=120, font=ctk.CTkFont(size=12))
        self.merge_list.pack(fill="x", padx=12, pady=8)

        self.merge_btn = ctk.CTkButton(tab, text="开始合并", height=36, font=ctk.CTkFont(size=14, weight="bold"), command=self._do_merge)
        self.merge_btn.pack(pady=8)

    def _build_split_tab(self, tab):
        ctk.CTkLabel(tab, text="将PDF按页码范围拆分为多个文件", text_color="gray").pack(pady=(8, 12))

        row1 = ctk.CTkFrame(tab, fg_color="transparent")
        row1.pack(fill="x", padx=12, pady=4)
        ctk.CTkButton(row1, text="选择PDF", command=self._select_split_file).pack(side="left", padx=4)
        self.split_file_label = ctk.CTkLabel(row1, text="未选择", text_color="gray")
        self.split_file_label.pack(side="left", padx=8)

        row2 = ctk.CTkFrame(tab, fg_color="transparent")
        row2.pack(fill="x", padx=12, pady=4)
        ctk.CTkLabel(row2, text="每份页数：").pack(side="left", padx=4)
        self.split_pages = ctk.CTkEntry(row2, width=80)
        self.split_pages.insert(0, "1")
        self.split_pages.pack(side="left", padx=4)
        ctk.CTkLabel(row2, text="（1=每页一个文件，2=每2页一个文件）").pack(side="left", padx=4)

        self.split_btn = ctk.CTkButton(tab, text="开始拆分", height=36, font=ctk.CTkFont(size=14, weight="bold"), command=self._do_split)
        self.split_btn.pack(pady=8)

    def _build_compress_tab(self, tab):
        ctk.CTkLabel(tab, text="压缩PDF文件体积，减少文件大小", text_color="gray").pack(pady=(8, 12))

        row = ctk.CTkFrame(tab, fg_color="transparent")
        row.pack(fill="x", padx=12, pady=4)
        ctk.CTkButton(row, text="选择PDF", command=self._select_compress_file).pack(side="left", padx=4)
        self.compress_file_label = ctk.CTkLabel(row, text="未选择", text_color="gray")
        self.compress_file_label.pack(side="left", padx=8)

        ctk.CTkLabel(tab, text="压缩质量：").pack(pady=(8, 2))
        self.compress_quality = ctk.CTkOptionMenu(tab, values=["高压缩(小文件)", "中压缩(平衡)", "低压缩(高质量)"])
        self.compress_quality.set("中压缩(平衡)")
        self.compress_quality.pack(pady=4)

        self.compress_btn = ctk.CTkButton(tab, text="开始压缩", height=36, font=ctk.CTkFont(size=14, weight="bold"), command=self._do_compress)
        self.compress_btn.pack(pady=8)

    def _build_watermark_tab(self, tab):
        ctk.CTkLabel(tab, text="给PDF添加文字水印", text_color="gray").pack(pady=(8, 12))

        row1 = ctk.CTkFrame(tab, fg_color="transparent")
        row1.pack(fill="x", padx=12, pady=4)
        ctk.CTkButton(row1, text="选择PDF", command=self._select_wm_file).pack(side="left", padx=4)
        self.wm_file_label = ctk.CTkLabel(row1, text="未选择", text_color="gray")
        self.wm_file_label.pack(side="left", padx=8)

        row2 = ctk.CTkFrame(tab, fg_color="transparent")
        row2.pack(fill="x", padx=12, pady=4)
        ctk.CTkLabel(row2, text="水印文字：").pack(side="left", padx=4)
        self.wm_text = ctk.CTkEntry(row2, width=200)
        self.wm_text.insert(0, "内部资料 禁止外传")
        self.wm_text.pack(side="left", padx=4)

        row3 = ctk.CTkFrame(tab, fg_color="transparent")
        row3.pack(fill="x", padx=12, pady=4)
        ctk.CTkLabel(row3, text="透明度：").pack(side="left", padx=4)
        self.wm_opacity = ctk.CTkSlider(row3, from_=0.1, to=1.0, width=150)
        self.wm_opacity.set(0.3)
        self.wm_opacity.pack(side="left", padx=4)
        ctk.CTkLabel(row3, text="字号：").pack(side="left", padx=(16, 4))
        self.wm_size = ctk.CTkEntry(row3, width=60)
        self.wm_size.insert(0, "36")
        self.wm_size.pack(side="left", padx=4)

        self.wm_btn = ctk.CTkButton(tab, text="添加水印", height=36, font=ctk.CTkFont(size=14, weight="bold"), command=self._do_watermark)
        self.wm_btn.pack(pady=8)

    def _add_merge_files(self):
        files = filedialog.askopenfilenames(filetypes=[("PDF", "*.pdf")])
        if files:
            self.merge_files.extend(files)
            self.merge_list.delete("1.0", "end")
            for i, f in enumerate(self.merge_files, 1):
                self.merge_list.insert("end", f"{i}. {os.path.basename(f)}\n{f}\n\n")

    def _clear_merge_files(self):
        self.merge_files.clear()
        self.merge_list.delete("1.0", "end")

    def _do_merge(self):
        if len(self.merge_files) < 2:
            return
        save_path = filedialog.asksaveasfilename(defaultextension=".pdf", filetypes=[("PDF", "*.pdf")], title="保存合并后的PDF")
        if not save_path:
            return
        self.merge_btn.configure(state="disabled", text="合并中...")
        threading.Thread(target=self._merge_worker, args=(list(self.merge_files), save_path), daemon=True).start()

    def _merge_worker(self, files, output):
        try:
            if HAS_PYMUPDF:
                result = fitz.open()
                for f in files:
                    doc = fitz.open(f)
                    result.insert_pdf(doc)
                    doc.close()
                result.save(output)
                result.close()
            elif HAS_PYPDF2:
                writer = PdfWriter()
                for f in files:
                    reader = PdfReader(f)
                    for page in reader.pages:
                        writer.add_page(page)
                with open(output, "wb") as f:
                    writer.write(f)
            else:
                raise RuntimeError("无PDF库可用")
            self.after(0, lambda: self._done("合并完成", output))
        except Exception as e:
            self.after(0, lambda: self._done(f"失败: {e}", None))

    def _select_split_file(self):
        f = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if f:
            self.split_file = f
            self.split_file_label.configure(text=os.path.basename(f))

    def _do_split(self):
        if not hasattr(self, "split_file"):
            return
        pages_per = int(self.split_pages.get() or "1")
        save_dir = filedialog.askdirectory(title="选择保存目录")
        if not save_dir:
            return
        self.split_btn.configure(state="disabled", text="拆分中...")
        threading.Thread(target=self._split_worker, args=(self.split_file, save_dir, pages_per), daemon=True).start()

    def _split_worker(self, filepath, save_dir, pages_per):
        try:
            if HAS_PYMUPDF:
                doc = fitz.open(filepath)
                total = len(doc)
                for start in range(0, total, pages_per):
                    out = fitz.open()
                    out.insert_pdf(doc, from_page=start, to_page=min(start + pages_per - 1, total - 1))
                    name = f"{os.path.splitext(os.path.basename(filepath))[0]}_p{start+1}-{min(start+pages_per, total)}.pdf"
                    out.save(os.path.join(save_dir, name))
                    out.close()
                doc.close()
            elif HAS_PYPDF2:
                reader = PdfReader(filepath)
                total = len(reader.pages)
                for start in range(0, total, pages_per):
                    writer = PdfWriter()
                    for i in range(start, min(start + pages_per, total)):
                        writer.add_page(reader.pages[i])
                    name = f"{os.path.splitext(os.path.basename(filepath))[0]}_p{start+1}-{min(start+pages_per, total)}.pdf"
                    with open(os.path.join(save_dir, name), "wb") as f:
                        writer.write(f)
            self.after(0, lambda: self._done("拆分完成", save_dir))
        except Exception as e:
            self.after(0, lambda: self._done(f"失败: {e}", None))

    def _select_compress_file(self):
        f = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if f:
            self.compress_file = f
            self.compress_file_label.configure(text=os.path.basename(f))

    def _do_compress(self):
        if not hasattr(self, "compress_file"):
            return
        save_path = filedialog.asksaveasfilename(defaultextension=".pdf", filetypes=[("PDF", "*.pdf")], title="保存压缩后的PDF")
        if not save_path:
            return
        quality_map = {"高压缩(小文件)": "screen", "中压缩(平衡)": "ebook", "低压缩(高质量)": "printer"}
        q = quality_map.get(self.compress_quality.get(), "ebook")
        self.compress_btn.configure(state="disabled", text="压缩中...")
        threading.Thread(target=self._compress_worker, args=(self.compress_file, save_path, q), daemon=True).start()

    def _compress_worker(self, filepath, output, quality):
        try:
            if HAS_PYMUPDF:
                doc = fitz.open(filepath)
                new_doc = fitz.open()
                scale = 1.0 if quality == "printer" else (1.5 if quality == "ebook" else 2.0)
                jpg_q = 80 if quality == "printer" else (60 if quality == "ebook" else 40)
                for page in doc:
                    pix = page.get_pixmap(matrix=fitz.Matrix(scale, scale))
                    img_data = pix.tobytes("jpeg", jpg_quality=jpg_q)
                    new_page = new_doc.new_page(width=page.rect.width, height=page.rect.height)
                    new_page.insert_image(new_page.rect, stream=img_data)
                doc.close()
                new_doc.save(output, garbage=4, deflate=True, clean=True)
                new_doc.close()
            else:
                raise RuntimeError("需要 PyMuPDF 进行压缩")
            orig = os.path.getsize(filepath)
            comp = os.path.getsize(output)
            ratio = (1 - comp / orig) * 100 if orig > 0 else 0
            self.after(0, lambda: self._done(f"压缩完成! {orig/1024:.0f}KB -> {comp/1024:.0f}KB (减少{ratio:.0f}%)", output))
        except Exception as e:
            self.after(0, lambda: self._done(f"失败: {e}", None))

    def _select_wm_file(self):
        f = filedialog.askopenfilename(filetypes=[("PDF", "*.pdf")])
        if f:
            self.wm_file = f
            self.wm_file_label.configure(text=os.path.basename(f))

    def _do_watermark(self):
        if not hasattr(self, "wm_file"):
            return
        save_path = filedialog.asksaveasfilename(defaultextension=".pdf", filetypes=[("PDF", "*.pdf")], title="保存加水印后的PDF")
        if not save_path:
            return
        text = self.wm_text.get().strip()
        opacity = float(self.wm_opacity.get())
        size = int(self.wm_size.get() or "36")
        self.wm_btn.configure(state="disabled", text="处理中...")
        threading.Thread(target=self._watermark_worker, args=(self.wm_file, save_path, text, opacity, size), daemon=True).start()

    def _watermark_worker(self, filepath, output, text, opacity, size):
        try:
            if HAS_PYMUPDF:
                doc = fitz.open(filepath)
                for page in doc:
                    rect = page.rect
                    w, h = rect.width, rect.height
                    wm = fitz.TextWriter(rect)
                    font = fitz.Font("helv")
                    scale = size
                    text_width = font.text_length(text, fontsize=scale)
                    angle = 45
                    x = (w - text_width * 0.7) / 2
                    y = h / 2
                    wm.append(font, (x, y), text, fontsize=scale)
                    page.write_text(wm, color=(0.8, 0.8, 0.8), fill_opacity=opacity, morph=(fitz.Point(x, y), fitz.Matrix(1, 1).prerotate(angle)))
                doc.save(output)
                doc.close()
            else:
                raise RuntimeError("需要 PyMuPDF 进行水印")
            self.after(0, lambda: self._done("水印添加完成", output))
        except Exception as e:
            self.after(0, lambda: self._done(f"失败: {e}", None))

    def _done(self, msg, path):
        from tkinter import messagebox
        for tab_name in ["合并", "拆分", "压缩", "水印"]:
            pass
        self.merge_btn.configure(state="normal", text="开始合并")
        self.split_btn.configure(state="normal", text="开始拆分")
        self.compress_btn.configure(state="normal", text="开始压缩")
        self.wm_btn.configure(state="normal", text="添加水印")
        if path:
            messagebox.showinfo("完成", f"{msg}\n\n保存到: {path}")
        else:
            messagebox.showerror("错误", msg)
