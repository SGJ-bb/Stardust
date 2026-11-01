# -*- coding: utf-8 -*-
"""wm_qban_resume.py - 断点续发：跳过已上架的 Q 版商品，只补发缺失的"""
import json
import sys
import urllib.error

sys.path.insert(0, r"f:\stradust")
from wm_qban_launch import (BASE, MODELS, DESC_TAIL, LINKS, PKG,
                            api, img_b64, login)
import os
import time


def published_titles(token):
    st, resp = api("GET", "/items", token, timeout=60)
    items = resp if isinstance(resp, list) else resp.get("items") or resp.get("data") or []
    titles = set()
    for it in items:
        t = it.get("item_title") or it.get("title") or ""
        if t:
            titles.add(t)
    return titles


def main():
    token = login()
    print("[AUTH] ok")
    titles = published_titles(token)
    print("[EXIST] %d items on record" % len(titles))
    for t in sorted(titles):
        if "Q版" in t or "女仆" in t:
            print("   -", t)

    # 重建商品定义（与 wm_qban_launch.main 完全一致）
    products = []
    for d, code, desc in MODELS:
        products.append((
            "AI直出Live2D模型%s %sQ版皮微瑕" % (code, desc),
            9.9, 25.0, (d, code, desc), [os.path.join(PKG, "qban_%s_preview.png" % d)],
        ))
    products.append((
        "AI直出Live2D模型Q版全家桶 6款少女Q版皮打包微瑕",
        39.9, 99.0, None, [os.path.join(PKG, "_bundle_preview.png")],
    ))

    for title, price, orig, lc, imgs in products:
        if title in titles:
            print("[SKIP] already published:", title)
            continue
        if lc:
            d, code, desc = lc
            desc_text = ("【款式】%s %s\n【发货】拍下自动秒发本款网盘链接（链接与卡券一致，提取码 1314）%s"
                         % (code, desc, DESC_TAIL))
        else:
            links_txt = "\n".join("· %s %s: %s 提取码 1314" % (c, x, LINKS[d]) for d, c, x in MODELS)
            desc_text = "【打包内容】6 款 Q版模型全部包含：\n%s%s" % (links_txt, DESC_TAIL)
        payload = {
            "account_id": "xy050786048733",
            "title": title,
            "description": desc_text,
            "price": price,
            "original_price": orig,
            "images": [img_b64(p) for p in imgs if os.path.exists(p)],
            "delivery_method": "无需邮寄",
            "postage": 0,
            "can_self_pickup": False,
            "condition": "全新",
        }
        if not payload["images"]:
            print("[PUB][ABORT] no preview image for", title)
            continue
        print("[PUB] publishing:", title, flush=True)
        try:
            st, resp = api("POST", "/product-publish", token, payload, timeout=900)
        except Exception as e:
            print("[PUB] EXC", type(e).__name__, e, flush=True)
            continue
        print("[PUB] HTTP", st, "->", json.dumps(resp, ensure_ascii=False)[:260], flush=True)
        time.sleep(2)

    # 最终核对
    titles2 = published_titles(token)
    print("[FINAL] titles containing Q版/全家桶:")
    for t in sorted(titles2):
        if "Q版" in t:
            print("   -", t)


if __name__ == "__main__":
    main()
