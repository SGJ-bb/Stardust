# -*- coding: utf-8 -*-
# 生成 theme/ViewThemeBinder.kt 里的 TEXT_TOKENS / BG_TOKENS 反查表（色值 -> 主题令牌）。
# 何时用：改过 res/values/colors.xml 或 res/values-night/colors.xml 之后重跑，
#         把生成的 _tmp_tokens.kt 覆盖回 ViewThemeBinder.kt 对应两张表。
# 注：脚本里的 MAP / HEX_TEXT / HEX_BG 是人工维护的「资源名 -> 令牌」决策表，
#     冲突由 PRIORITY 顺序裁决（越靠前优先级越高）。
# 运行：cd /f/stradust/android && python tools/gen_theme_token_map.py
import re, os
RES = r"F:/stradust/android/app/src/main/res"
def parse_colors(path):
    if not os.path.exists(path): return {}
    txt = open(path, encoding='utf-8').read()
    return dict(re.findall(r'<color\s+name="([^"]+)"\s*>\s*#([0-9a-fA-F]{3,8})\s*</color>', txt))
light = parse_colors(os.path.join(RES, "values", "colors.xml"))
night = parse_colors(os.path.join(RES, "values-night", "colors.xml"))

PRIORITY = ["TEXT_PRIMARY","TEXT_SECONDARY","TEXT_MUTED","TEXT_DISABLED",
  "PRIMARY","TERTIARY","SECONDARY","TERTIARY_CONTAINER","TEXT_LINK","ON_PRIMARY",
  "BACKGROUND","SURFACE","SURFACE_CONTAINER","SURFACE_CONTAINER_LOW","SURFACE_CONTAINER_HIGH",
  "OUTLINE","SURFACE_CONTAINER_HIGHEST","TOOLBAR","OUTLINE_VARIANT",
  "USER_BUBBLE_START","USER_BUBBLE_END","AI_BUBBLE"]
RANK = {t:i for i,t in enumerate(PRIORITY)}

MAP = {
 "text_primary":("TEXT_PRIMARY","T"),"color_text_input":("TEXT_PRIMARY","T"),
 "color_text_title":("TEXT_PRIMARY","T"),"color_text_chat_primary":("TEXT_PRIMARY","T"),
 "color_text_light_gray":("TEXT_PRIMARY","T"),
 "text_secondary":("TEXT_SECONDARY","T"),"color_text_chat_secondary":("TEXT_SECONDARY","T"),
 "color_text_label":("TEXT_SECONDARY","T"),"color_text_gray":("TEXT_SECONDARY","T"),
 "text_muted":("TEXT_MUTED","T"),"color_text_hint":("TEXT_MUTED","T"),
 "text_disabled":("TEXT_DISABLED","T"),"color_text_disabled":("TEXT_DISABLED","T"),
 "text_link":("TEXT_LINK","T"),"color_text_link":("TEXT_LINK","T"),
 "text_on_accent":("ON_PRIMARY","T"),
 "color_section_header":("PRIMARY","T"),"color_emotion_label":("PRIMARY","T"),
 "brand_primary":("PRIMARY","TB"),"brand_primary_light":("PRIMARY","TB"),
 "color_primary":("PRIMARY","TB"),"accent_blue":("PRIMARY","TB"),
 "brand_secondary":("TERTIARY","TB"),"brand_accent":("TERTIARY","TB"),
 "color_primary_light":("TERTIARY","TB"),"color_secondary":("TERTIARY","TB"),
 "accent_purple":("TERTIARY","TB"),"accent_pink":("TERTIARY","TB"),
 "accent_magenta":("TERTIARY","TB"),"brand_accent_container":("TERTIARY_CONTAINER","B"),
 "accent_cyan":("SECONDARY","TB"),"color_accent_teal":("SECONDARY","TB"),
 "bg_base":("BACKGROUND","B"),"bg_start":("BACKGROUND","B"),"bg_center":("BACKGROUND","B"),
 "bg_end":("BACKGROUND","B"),"color_background_github":("BACKGROUND","B"),
 "bg_elevated":("SURFACE_CONTAINER_LOW","B"),"surface_dark":("SURFACE","B"),
 "color_surface_github":("SURFACE","B"),"color_toolbar_bg":("TOOLBAR","B"),
 "surface_card":("SURFACE_CONTAINER","B"),"color_card_dark":("SURFACE_CONTAINER","B"),
 "color_card_dark_alt":("SURFACE_CONTAINER_HIGH","B"),
 "surface_input":("SURFACE_CONTAINER_HIGH","B"),"color_input_bg_variant":("SURFACE_CONTAINER_HIGH","B"),
 "surface_hover":("SURFACE_CONTAINER_HIGHEST","B"),"surface_selected":("SURFACE_CONTAINER_HIGHEST","B"),
 "color_card_selected":("SURFACE_CONTAINER_HIGHEST","B"),
 "stroke_subtle":("OUTLINE_VARIANT","B"),"divider":("OUTLINE_VARIANT","B"),
 "color_divider_dark":("OUTLINE_VARIANT","B"),
 "stroke_default":("OUTLINE","B"),"stroke":("OUTLINE","B"),"stroke_medium":("OUTLINE","B"),
 "stroke_emphasis":("OUTLINE","B"),"color_stroke_dark":("OUTLINE","B"),"color_stroke_medium":("OUTLINE","B"),
 "bubble_user_start":("USER_BUBBLE_START","B"),"bubble_user_end":("USER_BUBBLE_END","B"),
 "bubble_pet_start":("AI_BUBBLE","B"),"bubble_pet_end":("AI_BUBBLE","B"),
}
HEX_TEXT = {"e0e0f0":"TEXT_PRIMARY","e8e8f8":"TEXT_PRIMARY","e8e8f0":"TEXT_PRIMARY",
 "d0d0e8":"TEXT_PRIMARY","d0d0e0":"TEXT_PRIMARY","c8c8e0":"TEXT_PRIMARY",
 "e8e8e8":"TEXT_PRIMARY","333333":"TEXT_PRIMARY","1a1a2e":"TEXT_PRIMARY",
 "8899bb":"TEXT_SECONDARY","8899cc":"TEXT_SECONDARY","8899aa":"TEXT_SECONDARY",
 "8899dd":"TEXT_SECONDARY","667788":"TEXT_SECONDARY","666666":"TEXT_SECONDARY",
 "4b5563":"TEXT_SECONDARY","556677":"TEXT_MUTED","556688":"TEXT_MUTED",
 "aaaacc":"TEXT_MUTED","aabbcc":"TEXT_MUTED","999999":"TEXT_MUTED",
 "8888aa":"TEXT_MUTED","6b7280":"TEXT_MUTED","9ca3af":"TEXT_DISABLED",
 "aaaaaa":"TEXT_DISABLED","667eea":"PRIMARY","7c4dff":"PRIMARY","7c5cfc":"PRIMARY",
 "5c6bc0":"PRIMARY","3355aa":"PRIMARY","9c7cff":"TERTIARY","c4b5fd":"TERTIARY",
 "a78bfa":"TERTIARY","64ffda":"SECONDARY"}
HEX_BG = {"1a1a2e":"SURFACE","1a1a3e":"SURFACE_CONTAINER","1a1a38":"SURFACE_CONTAINER",
 "3a3a5a":"SURFACE_CONTAINER_HIGH","0d0d1a":"BACKGROUND","0d0d22":"BACKGROUND",
 "21262d":"SURFACE_CONTAINER","b316162a":"SURFACE_CONTAINER","cc0a0a1a":"SURFACE_CONTAINER",
 "e5e7eb":"OUTLINE_VARIANT","ededed":"OUTLINE_VARIANT","cfcfcf":"OUTLINE_VARIANT",
 "cccccc":"OUTLINE_VARIANT","f5f5f5":"SURFACE_CONTAINER","f8f9fa":"BACKGROUND",
 "667eea":"PRIMARY","9c7cff":"TERTIARY","64ffda":"SECONDARY"}

def to_int(h):
    h = h.lstrip('#')
    if len(h)==3: h=''.join(c*2 for c in h)
    if len(h)==6: h='ff'+h
    return int(h,16)

tbl_text, tbl_bg, log = {}, {}, []
def add(tbl, val, token, src):
    old = tbl.get(val)
    if old is not None and old != token:
        if RANK[token] < RANK[old]:
            tbl[val] = token; log.append((f"{val:#010x}", old, "->", token, src))
        else:
            log.append((f"{val:#010x}", old, "keep", token, src))
        return
    tbl[val] = token

for name,(token,scopes) in MAP.items():
    for src,d in (("L",light),("N",night)):
        if name not in d: continue
        v = to_int(d[name])
        if "T" in scopes: add(tbl_text, v, token, f"{name}/{src}")
        if "B" in scopes: add(tbl_bg, v, token, f"{name}/{src}")
for h,t in HEX_TEXT.items(): add(tbl_text, to_int(h), t, "hex")
for h,t in HEX_BG.items():   add(tbl_bg, to_int(h), t, "hex")

print("裁决记录:")
for l in log: print("  ", l)

def emit(tbl, cname):
    L=[f"    private val {cname}: Map<Int, ThemeToken> = buildMap {{"]
    for v,t in sorted(tbl.items()): L.append(f"        put(0x{v:08X}.toInt(), ThemeToken.{t})")
    L.append("    }")
    return "\n".join(L)
out = emit(tbl_text,"TEXT_TOKENS") + "\n\n" + emit(tbl_bg,"BG_TOKENS")
open(r"F:/stradust/android/_tmp_tokens.kt","w",encoding="utf-8").write(out)
print("\n文字表:",len(tbl_text),"条  背景表:",len(tbl_bg),"条")
