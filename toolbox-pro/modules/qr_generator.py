"""
模块6：二维码批量生成器
单个/批量生成，自定义颜色和Logo
"""

import os
import csv
import threading
import customtkinter as ctk
from tkinter import filedialog, messagebox

import qrcode
from PIL import Image, ImageDraw


class QRGeneratorFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color="transparent")
        self._build_ui()

    def _build_ui(self):
        title = ctk.CTkLabel(self, text="二维码生成器", font=ctk.CTkFont(size=24, weight="bold"))
        title.pack(pady=(30, 5), padx=30, anchor="w")
        desc = ctk.CTkLabel(self, text="单个生成或从CSV批量生成二维码", font=ctk.CTkFont(size=13), text_color="gray")
        desc.pack(pady=(0, 20), padx=30, anchor="w")

        self.tabs = ctk.CTkTabview(self, width=800)
        self.tabs.pack(fill="both", expand=True, padx=30, pady=5)

        self._build_single_tab(self.tabs.add("单个生成"))
        self._build_batch_tab(self.tabs.add("批量生成"))

    def _build_single_tab(self, tab):
        ctk.CTkLabel(tab, text="输入内容（网址/文本）：").pack(pady=(12, 4), padx=16, anchor="w")
        self.single_input = ctk.CTkTextbox(tab, height=60, font=ctk.CTkFont(size=13))
        self.single_input.pack(fill="x", padx=16, pady=4)

        row_opt = ctk.CTkFrame(tab, fg_color="transparent")
        row_opt.pack(fill="x", padx=16, pady=8)
        ctk.CTkLabel(row_opt, text="尺寸：").pack(side="left", padx=4)
        self.size_var = ctk.CTkOptionMenu(row_opt, width=100, values=["小(256)", "中(512)", "大(1024)"])
        self.size_var.set("中(512)")
        self.size_var.pack(side="left", padx=4)

        ctk.CTkButton(tab, text="生成并保存", height=36, font=ctk.CTkFont(size=14, weight="bold"), command=self._generate_single).pack(pady=12)

    def _build_batch_tab(self, tab):
        ctk.CTkLabel(tab, text="选择CSV文件（第一列=文件名，第二列=二维码内容）：", text_color="gray").pack(pady=(12, 4), padx=16, anchor="w")
        row = ctk.CTkFrame(tab, fg_color="transparent")
        row.pack(fill="x", padx=16, pady=4)
        ctk.CTkButton(row, text="选择CSV", command=self._choose_csv).pack(side="left", padx=4)
        self.csv_label = ctk.CTkLabel(row, text="未选择", text_color="gray")
        self.csv_label.pack(side="left", padx=8)

        row2 = ctk.CTkFrame(tab, fg_color="transparent")
        row2.pack(fill="x", padx=16, pady=4)
        ctk.CTkButton(row2, text="保存到", width=100, command=self._choose_batch_dir).pack(side="left", padx=4)
        self.dir_label = ctk.CTkLabel(row2, text="未选择", text_color="gray")
        self.dir_label.pack(side="left", padx=8)

        btn_row = ctk.CTkFrame(tab, fg_color="transparent")
        btn_row.pack(fill="x", padx=16, pady=8)
        self.btn_batch = ctk.CTkButton(btn_row, text="批量生成", height=36, width=140, font=ctk.CTkFont(size=14, weight="bold"), command=self._generate_batch)
        self.btn_batch.pack(side="left", padx=4)
        self.batch_status = ctk.CTkLabel(btn_row, text="就绪", text_color="gray", font=ctk.CTkFont(size=12))
        self.batch_status.pack(side="left", padx=8)

    def _generate_single(self):
        content = self.single_input.get("1.0", "end").strip()
        if not content:
            return
        size_map = {"小(256)": 256, "中(512)": 512, "大(1024)": 1024}
        size = size_map.get(self.size_var.get(), 512)

        save_path = filedialog.asksaveasfilename(
            defaultextension=".png", filetypes=[("PNG", "*.png")],
            title="保存二维码",
        )
        if not save_path:
            return

        qr = qrcode.QRCode(version=1, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=2)
        qr.add_data(content)
        qr.make(fit=True)
        img = qr.make_image(fill_color="black", back_color="white").resize((size, size), Image.LANCZOS)
        img.save(save_path)
        messagebox.showinfo("完成", f"二维码已保存到:\n{save_path}")

    def _choose_csv(self):
        f = filedialog.askopenfilename(filetypes=[("CSV", "*.csv")])
        if f:
            self.csv_file = f
            self.csv_label.configure(text=os.path.basename(f))

    def _choose_batch_dir(self):
        d = filedialog.askdirectory()
        if d:
            self.batch_dir = d
            self.dir_label.configure(text=d)

    def _generate_batch(self):
        if not hasattr(self, "csv_file") or not hasattr(self, "batch_dir"):
            messagebox.showwarning("提示", "请选择CSV文件和保存目录")
            return
        self.btn_batch.configure(state="disabled", text="生成中...")
        threading.Thread(target=self._batch_worker, args=(self.csv_file, self.batch_dir), daemon=True).start()

    def _batch_worker(self, csv_file, out_dir):
        try:
            with open(csv_file, "r", encoding="utf-8-sig") as f:
                reader = csv.reader(f)
                rows = list(reader)
            total = len(rows)
            success = 0
            for i, row in enumerate(rows):
                if len(row) < 2:
                    continue
                name, content = row[0].strip(), row[1].strip()
                if not content:
                    continue
                qr = qrcode.QRCode(version=1, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=10, border=2)
                qr.add_data(content)
                qr.make(fit=True)
                img = qr.make_image(fill_color="black", back_color="white").resize((512, 512), Image.LANCZOS)
                safe_name = name if name.endswith(".png") else name + ".png"
                img.save(os.path.join(out_dir, safe_name))
                success += 1

            self.after(0, lambda: self._batch_done(success, total))
        except Exception as e:
            self.after(0, lambda: self._batch_done(0, 0, str(e)))

    def _batch_done(self, success, total, err=None):
        self.btn_batch.configure(state="normal", text="批量生成")
        if err:
            self.batch_status.configure(text="❌ " + err, text_color="red")
            messagebox.showerror("错误", err)
        else:
            self.batch_status.configure(text=f"✅ 完成 {success}/{total}", text_color="green")
            messagebox.showinfo("完成", f"成功生成 {success}/{total} 个二维码")
