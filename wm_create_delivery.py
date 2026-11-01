# -*- coding: utf-8 -*-
"""wm_create_delivery.py - 为半身皮 Live2D 模型建发货卡券+规则"""
import json
import urllib.request
import sqlite3

BASE = "http://127.0.0.1:8090"
TOKEN_FILE = r"f:\stradust\_pub_token.txt"
DB = r"f:\stradust\xianyu-auto-reply-fix\data\xianyu_data.db"

CARD = {
    "name": "半身皮Live2D模型",
    "type": "text",
    "text_content": (
        "【Live2D 模型完整包 · 自动发货】\n"
        "链接: https://pan.baidu.com/s/1w5papo9VFDI9YSGH7XyJtg?pwd=1314\n"
        "提取码: 1314\n\n"
        "包含：33 层分层 PSD 源文件 + Live2D 模型完整包"
        "（moc3 / physics3 / model3 / 贴图 / 表情 / 动作）+ 效果验证图。\n"
        "使用：下载解压后，把模型文件夹拖进 VTube Studio 即可加载。\n"
        "链接失效请联系客服补发。祝直播顺利！"
    ),
    "enabled": True,
    "delay_seconds": 0,
}

RULE = {
    "keyword": "半身皮",
    "delivery_count": 1,
    "enabled": True,
    "description": "AI直出Live2D半身皮 模型包",
}


def api(method, path, token, payload=None):
    headers = {"Content-Type": "application/json", "Authorization": "Bearer " + token}
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def main():
    token = open(TOKEN_FILE).read().strip()

    # 防重：检查是否已有同名卡券/同关键词规则
    cards = api("GET", "/cards", token)
    for c in cards:
        if c.get("name") == CARD["name"]:
            print("[CARD] already exists id=%s, skip create" % c["id"])
            card_id = c["id"]
            break
    else:
        resp = api("POST", "/cards", token, CARD)
        card_id = resp.get("id") or resp.get("card", {}).get("id") or resp.get("data", {}).get("id")
        print("[CARD] created:", json.dumps(resp, ensure_ascii=False)[:200])
    print("[CARD] card_id =", card_id)

    rules = api("GET", "/delivery-rules", token)
    for r in rules:
        if r.get("keyword") == RULE["keyword"]:
            print("[RULE] already exists id=%s card_id=%s, update card_id" % (r["id"], r.get("card_id")))
            upd = dict(r)
            upd["card_id"] = card_id
            resp = api("PUT", "/delivery-rules/%s" % r["id"], token, upd)
            print("[RULE] updated:", json.dumps(resp, ensure_ascii=False)[:200])
            break
    else:
        payload = dict(RULE)
        payload["card_id"] = card_id
        resp = api("POST", "/delivery-rules", token, payload)
        print("[RULE] created:", json.dumps(resp, ensure_ascii=False)[:200])

    # 复核
    rules = api("GET", "/delivery-rules", token)
    for r in rules:
        if r.get("keyword") == "半身皮":
            print("[VERIFY] rule id=%s keyword=%s card_id=%s enabled=%s card=%s" % (
                r["id"], r["keyword"], r["card_id"], r["enabled"], r.get("card_name")))


if __name__ == "__main__":
    main()
