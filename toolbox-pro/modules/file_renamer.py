"""
模块5：文件批量重命名工具
序号命名 / 正则替换 / 添加前缀后缀
"""

import os
import re
import threading
import customtkinter as ctk
from tkinter import filedialog, messagebox


class FileRenamerFrame(ctk.CTkFrame):
    def __init__(self, parent, app):
        super().__init__(parent, fg_color="transparent")
        self.files = []
        self._build_ui()

    def _build_ui(self):
        title = ctk.CTkLabel(self, text="文件批量重命名", font=ctk.CTkFont(size=24, weight="bold"))
        title.pack(pady=(30, 5), padx=30, anchor="w")
        desc = ctk.CTkLabel(self, text="支持序号命名、正则替换、添加前后缀", font=ctk.CTkFont(size=13), text_color="gray")
        desc.pack(pady=(0, 20), padx=30, anchor="w")

        card = ctk.CTkFrame(self, corner_radius=12)
        card.pack(fill="both", expand=True, padx=30, pady=5)

        row1 = ctk.CTkFrame(card, fg_color="transparent")
        row1.pack(fill="x", padx=20, pady=(16, 4))
        ctk.CTkButton(row1, text="选择文件", command=self._choose_files).pack(side="left", padx=4)
        ctk.CTkButton(row1, text="选择文件夹", command=self._choose_folder).pack(side="left", padx=4)
        self.count_label = ctk.CTkLabel(row1, text="0 个文件", text_color="gray")
        self.count_label.pack(side="left", padx=8)

        ctk.CTkLabel(card, text="重命名模式：", font=ctk.CTkFont(size=14)).pack(pady=(8, 4), padx=20, anchor="w")
        self.mode_var = ctk.CTkOptionMenu(
            card, width=200,
            values=["序号命名", "正则替换", "添加前缀", "添加后缀", "替换文字"],
        )
        self.mode_var.set("序号命名")
        self.mode_var.pack(padx=20, pady=4, anchor="w")

        row_opt = ctk.CTkFrame(card, fg_color="transparent")
        row_opt.pack(fill="x", padx=20, pady=4)

        ctk.CTkLabel(row_opt, text="参数：").pack(side="left", padx=4)
        self.param_entry = ctk.CTkEntry(row_opt, width=300)
        self.param_entry.insert(0, "file_")
        self.param_entry.pack(side="left", padx=4)

        ctk.CTkLabel(card, text="序号命名：参数=前缀，如 file_ → file_1.jpg, file_2.jpg").pack(padx=20, pady=2, anchor="w")
        ctk.CTkLabel(card, text="正则替换：参数=旧|新，如 old|new").pack(padx=20, pady=2, anchor="w")
        ctk.CTkLabel(card, text="添加前缀/后缀：参数=要添加的文字").pack(padx=20, pady=2, anchor="w")
        ctk.CTkLabel(card, text="替换文字：参数=旧|新").pack(padx=20, pady=2, anchor="w")

        row_start = ctk.CTkLabel(card, text="起始序号：")
        row_start.pack(padx=20, pady=(8, 0), anchor="w")
        self.start_num = ctk.CTkEntry(card, width=80)
        self.start_num.insert(0, "1")
        self.start_num.pack(padx=20, pady=2, anchor="w")

        self.preview_text = ctk.CTkTextbox(card, height=150, font=ctk.CTkFont(size=12))
        self.preview_text.pack(fill="both", expand=True, padx=20, pady=8)

        btn_row = ctk.CTkFrame(card, fg_color="transparent")
        btn_row.pack(fill="x", padx=20, pady=(4, 16))
        ctk.CTkButton(btn_row, text="预览", width=100, command=self._preview).pack(side="left", padx=4)
        self.btn = ctk.CTkButton(btn_row, text="执行重命名", height=36, width=140, font=ctk.CTkFont(size=14, weight="bold"), command=self._execute)
        self.btn.pack(side="left", padx=4)
        self.status = ctk.CTkLabel(btn_row, text="就绪", text_color="gray", font=ctk.CTkFont(size=12))
        self.status.pack(side="left", padx=8)

    def _choose_files(self):
        files = filedialog.askopenfilenames()
        if files:
            self.files = list(files)
            self.count_label.configure(text=f"{len(self.files)} 个文件")

    def _choose_folder(self):
        d = filedialog.askdirectory()
        if d:
            self.files = [os.path.join(d, f) for f in os.listdir(d) if os.path.isfile(os.path.join(d, f))]
            self.count_label.configure(text=f"{len(self.files)} 个文件")

    def _compute_names(self):
        mode = self.mode_var.get()
        param = self.param_entry.get().strip()
        start = int(self.start_num.get() or "1")
        pairs = []
        for i, filepath in enumerate(self.files):
            d = os.path.dirname(filepath)
            old_name = os.path.basename(filepath)
            name, ext = os.path.splitext(old_name)
            if mode == "序号命名":
                new_name = f"{param}{start + i}{ext}"
            elif mode == "正则替换":
                parts = param.split("|", 1)
                if len(parts) == 2:
                    new_name = re.sub(parts[0], parts[1], old_name)
                else:
                    new_name = old_name
            elif mode == "添加前缀":
                new_name = f"{param}{old_name}"
            elif mode == "添加后缀":
                new_name = f"{name}{param}{ext}"
            elif mode == "替换文字":
                parts = param.split("|", 1)
                if len(parts) == 2:
                    new_name = old_name.replace(parts[0], parts[1])
                else:
                    new_name = old_name
            else:
                new_name = old_name
            pairs.append((filepath, os.path.join(d, new_name), old_name, new_name))
        return pairs

    def _preview(self):
        if not self.files:
            return
        pairs = self._compute_names()
        self.preview_text.delete("1.0", "end")
        for old_path, new_path, old_name, new_name in pairs:
            self.preview_text.insert("end", f"{old_name}  →  {new_name}\n")

    def _execute(self):
        if not self.files:
            messagebox.showwarning("提示", "请先选择文件")
            return
        pairs = self._compute_names()
        self.btn.configure(state="disabled", text="重命名中...")
        success = 0
        for old_path, new_path, old_name, new_name in pairs:
            try:
                if old_path != new_path:
                    os.rename(old_path, new_path)
                success += 1
            except Exception:
                pass
        self.btn.configure(state="normal", text="执行重命名")
        self.status.configure(text=f"✅ 完成 {success}/{len(pairs)}", text_color="green")
        messagebox.showinfo("完成", f"成功重命名 {success}/{len(pairs)} 个文件")
