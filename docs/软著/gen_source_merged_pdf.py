# -*- coding: utf-8 -*-
"""星尘软著——源程序鉴别材料合并PDF
软著要求"前连续30页 + 后连续30页"，且只能上传一个文件。
本脚本将全部源码按文件排序拼接，取前1500行(前30页) + 后1500行(后30页)，
合并为一份60页PDF，中间通过分页符与页眉标注区分。
"""
import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Spacer, Table, TableStyle, PageBreak
from reportlab.lib import colors

SRC_DIR = r"f:\stradust\android\app\src\main\java"
OUT_PATH = r"f:\stradust\docs\软著\星尘软著-源程序前后30页合并版.pdf"

FONT_CJK = r"C:\Windows\Fonts\msyh.ttc"
FONT_HEAD = r"C:\Windows\Fonts\msyhbd.ttc"
FALLBACK = r"C:\Windows\Fonts\simsun.ttc"


def reg_fonts():
    cjk = FONT_CJK if os.path.exists(FONT_CJK) else FALLBACK
    try:
        pdfmetrics.registerFont(TTFont("CJK", cjk))
    except Exception:
        pdfmetrics.registerFont(TTFont("CJK", FALLBACK))
    try:
        pdfmetrics.registerFont(
            TTFont("CJK-B", FONT_HEAD if os.path.exists(FONT_HEAD) else FALLBACK))
    except Exception:
        pdfmetrics.registerFont(TTFont("CJK-B", cjk))


def collect_source():
    files = []
    for root, _, names in os.walk(SRC_DIR):
        if "\\build\\" in root + "\\":
            continue
        for n in names:
            if n.endswith(".kt"):
                files.append(os.path.join(root, n))
    files.sort()
    flat = []
    for f in files:
        with open(f, encoding="utf-8", errors="replace") as fh:
            flat.extend(fh.read().splitlines())
    return flat


def build_pdf(path):
    reg_fonts()
    doc = SimpleDocTemplate(
        path, pagesize=A4,
        leftMargin=14*mm, rightMargin=14*mm,
        topMargin=14*mm, bottomMargin=14*mm,
        title="星尘源程序鉴别材料（前30页+后30页）",
    )
    story = []
    flat = collect_source()
    total = len(flat)
    first = flat[:1500]
    last = flat[-1500:]

    # 首节：前30页
    for start in range(0, len(first), 50):
        chunk = first[start:start+50]
        rows = []
        lineno = start + 1
        for ln in chunk:
            rows.append([str(lineno), ln if ln else " "])
            lineno += 1
        while len(rows) < 50:
            rows.append([str(lineno), " "]); lineno += 1
        t = Table(rows, colWidths=[10*mm, 172*mm])
        t.setStyle(TableStyle([
            ('FONTNAME', (0,0), (-1,-1), 'CJK'),
            ('FONTSIZE', (0,0), (-1,-1), 8),
            ('LEFTPADDING', (0,0), (-1,-1), 0),
            ('RIGHTPADDING', (0,0), (-1,-1), 2),
            ('TOPPADDING', (0,0), (-1,-1), 0),
            ('BOTTOMPADDING', (0,0), (-1,-1), 0),
            ('LINEBELOW', (0,0), (-1,-1), 0.2, colors.Color(0,0,0,0.25)),
        ]))
        story.append(t)
        story.append(Spacer(1, 6))

    story.append(PageBreak())
    # 后30页：为清晰区分，页内行号从 后30页第一行继续（不重置为1）
    base_lineno = total - len(last) + 1
    for start in range(0, len(last), 50):
        chunk = last[start:start+50]
        rows = []
        lineno = base_lineno + start
        for ln in chunk:
            rows.append([str(lineno), ln if ln else " "])
            lineno += 1
        while len(rows) < 50:
            rows.append([str(lineno), " "]); lineno += 1
        t = Table(rows, colWidths=[10*mm, 172*mm])
        t.setStyle(TableStyle([
            ('FONTNAME', (0,0), (-1,-1), 'CJK'),
            ('FONTSIZE', (0,0), (-1,-1), 8),
            ('LEFTPADDING', (0,0), (-1,-1), 0),
            ('RIGHTPADDING', (0,0), (-1,-1), 2),
            ('TOPPADDING', (0,0), (-1,-1), 0),
            ('BOTTOMPADDING', (0,0), (-1,-1), 0),
            ('LINEBELOW', (0,0), (-1,-1), 0.2, colors.Color(0,0,0,0.25)),
        ]))
        story.append(t)
        story.append(Spacer(1, 6))

    def header_footer(canv, d):
        canv.saveState()
        canv.setFont("CJK", 9)
        page = canv.getPageNumber()
        section = "前半部分（前30页）" if page <= 30 else "后半部分（后30页）"
        canv.drawString(14*mm, A4[1]-10*mm,
                        "星尘（AI桌宠伴侣）软件 · 源程序鉴别材料 · " + section)
        canv.drawRightString(A4[0]-14*mm, A4[1]-10*mm, "第 %d / 60 页" % page)
        canv.restoreState()

    doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
    print("合并PDF生成完成，总行数=%d，前30页行号1~1500，后30页行号%d~%d"
          % (total, base_lineno, total))
    print("路径:", path)


if __name__ == "__main__":
    build_pdf(OUT_PATH)