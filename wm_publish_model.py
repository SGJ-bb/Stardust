# -*- coding: utf-8 -*-
"""wm_publish_model.py - 上架女仆御姐半身皮 Live2D 模型到闲鱼
价格锚点: B站量贩公版皮 25-50 / StreamSkins 成品 191 / 本店鲸鱼娘 49.9 / OC定制 299
定位: 现价 49.9 / 原价 88, AI直出标注, 非独家公版
发货关键词: 半身皮 (唯一命中, 已验证与现有 10 条规则/17 条商品文本零冲突)
"""
import base64
import json
import os
import time
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8090"
TOKEN_FILE = r"f:\stradust\_pub_token.txt"
ACCOUNT_ID = "xy050786048733"
OUT = r"F:\image2psd2live\out\whitemaid_green"

TITLE = "AI直出Live2D半身皮 女仆御姐六表情VTS皮套"
PRICE = 49.9
ORIGINAL_PRICE = 88.0

DESCRIPTION = (
    "【重要 · AI直出标注】\n"
    "本模型形象为 AI 生成立绘 + 人工分层精修与 Live2D 绑定，非画师手绘约稿，介意慎拍。\n"
    "非独家公版皮（多人可购同款），购买即授权一人一皮。\n\n"
    "【你将得到】\n"
    "· 半身女仆御姐 Live2D 模型全套（moc3 + 物理 + 贴图）\n"
    "· 33 层分层 PSD 源文件（可自行改色 / 二改）\n"
    "· 表情参数说明\n\n"
    "【模型功能】\n"
    "· 六表情按键：生气怒目 / 花痴爱心瞳 / 伤心泪眼 / 单眼wink / 傲娇半睁 / 微醺脸红\n"
    "· 口型同步（麦克风驱动说话张嘴）\n"
    "· 自动眨眼 / 眼球追踪 / 头部XYZ转动 / 身体摇摆\n"
    "· 头发物理飘动\n\n"
    "【使用要求】\n"
    "· 仅支持 Windows 电脑端 VTube Studio / PrprLive，手机不可用\n"
    "· 下载解压后把模型文件夹拖进 VTS 即可使用\n\n"
    "【发货方式】\n"
    "拍下付款后系统自动秒发网盘链接，无需等待。\n"
    "虚拟商品，发货后不退不换；链接失效联系客服补发。\n\n"
    "【授权声明】\n"
    "一人一皮：可用于直播 / 视频投稿；禁止转卖、二次分享、冒充自制。"
)

IMAGES = [
    os.path.join(OUT, "_preview_full.png"),
    os.path.join(OUT, "_verify_expr6_grid.png"),
    os.path.join(OUT, "_verify_expr_eyes.png"),
    os.path.join(OUT, "_verify_turn_gaze.png"),
]


def api(method, path, token, payload=None, timeout=180):
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


def main():
    token = open(TOKEN_FILE).read().strip()
    status, resp = api("GET", "/cookies", token)
    if status == 401:
        print("[AUTH] token expired")
        return
    print("[AUTH] ok")

    payload = {
        "account_id": ACCOUNT_ID,
        "title": TITLE,
        "description": DESCRIPTION,
        "price": PRICE,
        "original_price": ORIGINAL_PRICE,
        "images": [img_b64(p) for p in IMAGES],
        "delivery_method": "无需邮寄",
        "postage": 0,
        "can_self_pickup": False,
        "condition": "全新",
    }
    print("[PUB] publishing:", TITLE, "(%d chars title)" % len(TITLE))
    status, resp = api("POST", "/product-publish", token, payload)
    print("[PUB] HTTP", status, "->", json.dumps(resp, ensure_ascii=False)[:400])


if __name__ == "__main__":
    main()
