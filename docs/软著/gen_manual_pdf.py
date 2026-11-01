# -*- coding: utf-8 -*-
"""星尘软著——使用说明书 PDF 生成
软著文档鉴别材料：前30页 + 后30页。文档不足60页时提交全部（一份即可）。
本脚本将 Markdown 说明书排版为 A4，并输出总页数，若 <60 页则生成一份完整版（前后各同一份）供上传。
"""
import re
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT, TA_CENTER

MD_PATH = r"f:\stradust\docs\软著\星尘软件使用说明书.md"
OUT_PATH = r"f:\stradust\docs\软著\星尘软著-使用说明书.pdf"

FONT_CJK = r"C:\Windows\Fonts\msyh.ttc"
FONT_HEAD = r"C:\Windows\Fonts\msyhbd.ttc"
FALLBACK = r"C:\Windows\Fonts\simsun.ttc"


def reg_fonts():
    cjk = FONT_CJK if __import__("os").path.exists(FONT_CJK) else FALLBACK
    try:
        pdfmetrics.registerFont(TTFont("CJK", cjk))
    except Exception:
        pdfmetrics.registerFont(TTFont("CJK", FALLBACK))
    try:
        pdfmetrics.registerFont(TTFont("CJK-B",
                                FONT_HEAD if __import__("os").path.exists(FONT_HEAD) else FALLBACK))
    except Exception:
        pdfmetrics.registerFont(TTFont("CJK-B", cjk))


def read_lines():
    with open(MD_PATH, encoding="utf-8") as f:
        return f.readlines()


def convert(story):
    body = ParagraphStyle("body", fontName="CJK", fontSize=11, leading=17,
                          spaceAfter=4, alignment=TA_LEFT)
    h1 = ParagraphStyle("h1", fontName="CJK-B", fontSize=18, leading=24,
                        spaceAfter=10, alignment=TA_CENTER)
    h2 = ParagraphStyle("h2", fontName="CJK-B", fontSize=15, leading=20,
                        spaceBefore=12, spaceAfter=6, alignment=TA_LEFT)
    h3 = ParagraphStyle("h3", fontName="CJK-B", fontSize=12.5, leading=17,
                        spaceBefore=8, spaceAfter=4, alignment=TA_LEFT)
    for raw in read_lines():
        line = raw.rstrip("\n")
        if not line.strip():
            continue
        if line.startswith("# "):
            story.append(Paragraph(_esc(line[2:].strip()), h1))
        elif line.startswith("## "):
            story.append(Paragraph(_esc(line[3:].strip()), h2))
        elif line.startswith("### "):
            story.append(Paragraph(_esc(line[4:].strip()), h3))
        elif line.startswith("- "):
            story.append(Paragraph("•　" + _esc(line[2:].strip()), body))
        else:
            story.append(Paragraph(_esc(line.strip()), body))


def _esc(s):
    # reportlab 需要转义保留 & < >
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def header_footer(canv, d):
    canv.saveState()
    canv.setFont("CJK", 9)
    canv.drawCentredString(A4[0]/2, A4[1]-11*mm, "星尘（AI桌宠伴侣）软件 · 使用说明书")
    canv.drawCentredString(A4[0]/2, 10*mm, "第 %d 页" % canv.getPageNumber())
    canv.restoreState()


def main():
    reg_fonts()
    doc = SimpleDocTemplate(OUT_PATH, pagesize=A4,
                            leftMargin=18*mm, rightMargin=18*mm,
                            topMargin=16*mm, bottomMargin=16*mm,
                            title="星尘软件使用说明书")
    story = []
    convert(story)
    doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
    # 统计页数
    from pypdf import PdfReader
    try:
        n = len(PdfReader(OUT_PATH).pages)
        print("说明书PDF生成完成，共 %d 页" % n)
        print("路径:", OUT_PATH)
        if n <= 60:
            print("提示：文档不足60页，鉴别材料提交全部即可（前30页与后30页均使用本文件）。")
        else:
            print("提示：文档超过60页，需再按页拆分为前30页/后30页两份。")
    except Exception as e:
        print("说明书PDF生成完成（无法统计页数：%s）" % e)


if __name__ == "__main__":
    main()