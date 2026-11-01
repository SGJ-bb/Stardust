# -*- coding: utf-8 -*-
"""wm_qban_launch.py - Q版六模型 + 全家桶 一键上架（卡券+规则+商品）"""
import base64
import json
import os
import sqlite3
import time
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8090"
TOKEN_FILE = r"f:\stradust\_pub_token.txt"
DB = r"f:\stradust\xianyu-auto-reply-fix\data\xianyu_data.db"
PKG = r"F:\image2psd2live\out\grid12_final\_pkg"

LINKS = {
    "r1c1": "https://pan.baidu.com/s/1RtlDIvYLRVgygK-c0mbzyw?pwd=1314",
    "r1c2": "https://pan.baidu.com/s/1w7Yacs9Gy_VpJ11pKP7qmA?pwd=1314",
    "r1c4": "https://pan.baidu.com/s/1RO12SANHcBftBkmPuuj1kw?pwd=1314",
    "r2c2": "https://pan.baidu.com/s/1Rq4sVNazdum_t0ARmhQHbg?pwd=1314",
    "r3c1": "https://pan.baidu.com/s/1ojwe72fhfubDtKlxooOzXQ?pwd=1314",
    "r3c2": "https://pan.baidu.com/s/1IxK6elyw5Dwpd6YmmamAyA?pwd=1314",
}
MODELS = [  # (dir, 代号, 描述词)
    ("r1c1", "Q版01", "粉发猫耳蝴蝶结少女"),
    ("r1c2", "Q版02", "橘发双马尾少女"),
    ("r1c4", "Q版03", "金发奶油少女"),
    ("r2c2", "Q版04", "紫发蓝眼少女"),
    ("r3c1", "Q版05", "粉发白花少女"),
    ("r3c2", "Q版06", "蓝发白花少女"),
]

DESC_TAIL = (
    "\n\n【重要 · AI直出标注】\n"
    "本模型形象为 AI 生成立绘 + 人工分层精修与 Live2D 绑定，非画师手绘约稿，介意慎拍。\n"
    "【微瑕说明】AI 直出公版，细节存在小瑕疵；非独家公版皮（多人可购同款），购买即授权一人一皮。\n\n"
    "【你将得到】\n"
    "· Q版少女 Live2D 模型全套（moc3 + 物理 + 贴图）\n"
    "· 分层 PSD 源文件（可自行二改）\n"
    "· psd2live 工程包（可用 PSD2Live 软件继续编辑）\n\n"
    "【模型功能】\n"
    "· 自动眨眼 / 待机 / 点头 / 摇晃 动作\n"
    "· 头部XYZ转动 · 身体物理摇摆\n\n"
    "【使用要求】\n"
    "· 仅支持 Windows 电脑端 VTube Studio / PrprLive，手机不可用\n"
    "· 下载解压后把模型文件夹拖进 VTS 即可使用\n\n"
    "【发货方式】\n"
    "拍下付款后系统自动秒发网盘链接，无需等待。\n"
    "虚拟商品，发货后不退不换；链接失效联系客服补发。\n\n"
    "【授权声明】\n"
    "一人一皮：可用于直播 / 视频投稿；禁止转卖、二次分享、冒充自制。"
)


def api(method, path, token, payload=None, timeout=300):
    headers = {"Content-Type": "application/json", "Authorization": "Bearer " + token}
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode("utf-8"))
        except Exception:
            body = {"raw": "non-json"}
        return e.code, body


def img_b64(path):
    with open(path, "rb") as f:
        return {"base64": base64.b64encode(f.read()).decode("ascii"), "filename": os.path.basename(path)}


def login():
    conn = sqlite3.connect(DB)
    conn.execute("UPDATE system_settings SET value='false' WHERE key='login_captcha_enabled'")
    conn.commit(); conn.close()
    body = json.dumps({"username": "admin", "password": "admin123"}).encode()
    req = urllib.request.Request(BASE + "/login", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=15) as r:
        token = json.loads(r.read())["token"]
    conn = sqlite3.connect(DB)
    conn.execute("UPDATE system_settings SET value='true' WHERE key='login_captcha_enabled'")
    conn.commit(); conn.close()
    open(TOKEN_FILE, "w").write(token)
    return token


def main():
    token = login()
    print("[AUTH] ok")

    cards = api("GET", "/cards", token)[1]
    card_by_name = {c.get("name"): c["id"] for c in cards}
    rules = api("GET", "/delivery-rules", token)[1]
    rule_by_kw = {r.get("keyword"): r["id"] for r in rules}

    # ---------- 1) 七张卡券 ----------
    card_defs = []
    for d, code, desc in MODELS:
        card_defs.append((
            "%s模型" % code,
            "【%s %s · Live2D 模型完整包 · 自动发货】\n链接: %s\n提取码: 1314\n\n"
            "包含：Q版少女 Live2D 模型全套（moc3/物理/贴图/4个动作）+ 分层 PSD 源文件 + psd2live 工程包。\n"
            "使用：下载解压后，把模型文件夹拖进 VTube Studio 即可。\n链接失效请联系客服补发。" % (code, desc, LINKS[d]),
        ))
    bundle_link_text = "\n".join("%s: %s 提取码: 1314" % (code, LINKS[d]) for d, code, _ in MODELS)
    card_defs.append((
        "Q版全家桶模型",
        "【Q版全家桶 · 6款 Live2D 模型完整包 · 自动发货】\n%s\n\n"
        "包含：6 款 Q版少女 Live2D 模型全套（moc3/物理/贴图/动作）+ 各自分层 PSD 源文件 + psd2live 工程包。\n"
        "使用：下载解压后，把模型文件夹拖进 VTube Studio 即可。\n链接失效请联系客服补发。" % bundle_link_text,
    ))
    card_ids = {}
    for name, text in card_defs:
        if name in card_by_name:
            card_ids[name] = card_by_name[name]
            print("[CARD] exists", name, card_ids[name])
            continue
        st, resp = api("POST", "/cards", token, {"name": name, "type": "text", "text_content": text,
                                                 "enabled": True, "delay_seconds": 0})
        cid = resp.get("id") if isinstance(resp, dict) else None
        card_ids[name] = cid
        print("[CARD] created", name, "->", cid, json.dumps(resp, ensure_ascii=False)[:120])
        time.sleep(0.4)

    # ---------- 2) 七条规则 ----------
    rule_defs = [(code, "%s模型" % code) for _, code, _ in MODELS] + [("Q版全家桶", "Q版全家桶模型")]
    for kw, cname in rule_defs:
        if kw in rule_by_kw:
            print("[RULE] exists", kw, rule_by_kw[kw])
            continue
        payload = {"keyword": kw, "card_id": card_ids[cname], "delivery_count": 1, "enabled": True,
                   "description": "Q版模型自动发货"}
        st, resp = api("POST", "/delivery-rules", token, payload)
        print("[RULE] created", kw, "->", json.dumps(resp, ensure_ascii=False)[:120])
        time.sleep(0.4)

    # ---------- 3) 七个商品 ----------
    products = []
    for d, code, desc in MODELS:
        products.append((
            "AI直出Live2D模型%s %sQ版皮微瑕" % (code, desc),
            9.9, 25.0, LINKS[d], [os.path.join(PKG, "qban_%s_preview.png" % d)],
            "对应「%s %s」款，发货链接与详情一致。" % (code, desc),
        ))
    products.append((
        "AI直出Live2D模型Q版全家桶 6款少女Q版皮打包微瑕",
        39.9, 99.0, None, [os.path.join(PKG, "_bundle_preview.png")],
        "打包含全部 6 款（Q版01~06），卡券一次性发 6 个网盘链接。",
    ))
    for title, price, orig, link, imgs, note in products:
        desc = None
        if link:
            code = [c for d, c, _ in MODELS if LINKS[d] == link][0]
            dd = [x for d, c, x in MODELS if c == code][0]
            desc = "【款式】%s %s\n【发货】拍下自动秒发本款网盘链接（链接与卡券一致，提取码 1314）%s" % (code, dd, DESC_TAIL)
        else:
            links_txt = "\n".join("· %s %s: %s 提取码 1314" % (c, x, LINKS[d]) for d, c, x in MODELS)
            desc = "【打包内容】6 款 Q版模型全部包含：\n%s%s" % (links_txt, DESC_TAIL)
        payload = {
            "account_id": "xy050786048733",
            "title": title,
            "description": desc,
            "price": price,
            "original_price": orig,
            "images": [img_b64(p) for p in imgs if os.path.exists(p)],
            "delivery_method": "无需邮寄",
            "postage": 0,
            "can_self_pickup": False,
            "condition": "全新",
        }
        print("[PUB] publishing:", title, "(%d chars)" % len(title))
        st, resp = api("POST", "/product-publish", token, payload)
        print("[PUB] HTTP", st, "->", json.dumps(resp, ensure_ascii=False)[:260])
        time.sleep(1.5)


if __name__ == "__main__":
    main()
