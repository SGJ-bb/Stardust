"""
内置表情包预计算脚本
用法: python generate_builtin_embeddings.py --api_url <embedding_api_url> --api_key <key> --model <model_name>

示例:
python generate_builtin_embeddings.py \
  --api_url https://api.openai.com/v1 \
  --api_key sk-xxx \
  --model text-embedding-3-small

输出:
  assets/builtin_stickers/builtin_metadata.json  - 表情包元数据
  assets/builtin_stickers/builtin_embeddings.bin  - 向量二进制文件

bin格式:
  [4字节 int32: 向量维度]
  [4字节 int32: 表情包数量]
  [每个表情包: dim*4字节 float32向量]
"""

import os
import json
import struct
import argparse
import urllib.request
import urllib.error


EMOTION_DIR = os.path.join(os.path.dirname(os.pathfile__), "emotion")
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "android", "app", "src", "main", "assets", "builtin_stickers")


def get_sticker_description(filename: str) -> dict:
    name = os.path.splitext(filename)[0]
    emotion_map = {
        "偷听": {"emotion": "好奇", "tags": ["偷听", "八卦", "好奇", "窥探"], "description": "偷偷听别人说话，好奇心满满"},
        "偷瞄": {"emotion": "害羞", "tags": ["偷瞄", "害羞", "偷偷看", "腼腆"], "description": "偷偷瞄一眼，有点害羞"},
        "卖萌揣手手": {"emotion": "可爱", "tags": ["卖萌", "揣手", "可爱", "撒娇", "乖巧"], "description": "揣着小手卖萌，超级可爱"},
        "吐槽": {"emotion": "无语", "tags": ["吐槽", "无语", "嫌弃", "不满", "抱怨"], "description": "忍不住吐槽，表示不满"},
        "呆滞": {"emotion": "发呆", "tags": ["呆滞", "发呆", "放空", "宕机", "懵"], "description": "大脑空白，呆呆地发愣"},
        "哭泣": {"emotion": "难过", "tags": ["哭泣", "难过", "伤心", "委屈", "哭"], "description": "伤心地哭泣，需要安慰"},
        "宕机": {"emotion": "崩溃", "tags": ["宕机", "崩溃", "死机", "卡住", "当机"], "description": "大脑宕机了，完全无法思考"},
        "慌张": {"emotion": "慌张", "tags": ["慌张", "紧张", "害怕", "慌乱", "着急"], "description": "慌慌张张，不知所措"},
        "捂嘴笑": {"emotion": "偷笑", "tags": ["捂嘴笑", "偷笑", "坏笑", "窃喜", "开心"], "description": "捂着嘴偷笑，心里美滋滋"},
        "调侃": {"emotion": "调侃", "tags": ["调侃", "戏弄", "打趣", "开玩笑", "逗"], "description": "调侃别人，带着玩味的笑"},
        "邪恶的笑": {"emotion": "坏笑", "tags": ["邪恶", "坏笑", "阴谋", "不怀好意", "腹黑"], "description": "邪恶地笑，心里打着坏主意"},
        "风趣调侃": {"emotion": "幽默", "tags": ["风趣", "幽默", "调侃", "机智", "搞笑"], "description": "风趣地调侃，幽默感十足"},
        "骂别人是猪": {"emotion": "嫌弃", "tags": ["骂人", "嫌弃", "吐槽", "笨蛋", "猪"], "description": "骂别人是猪，嫌弃的样子"},
        "鬼迷日眼的笑": {"emotion": "狡黠", "tags": ["鬼迷日眼", "狡黠", "贼笑", "诡异", "不怀好意"], "description": "鬼迷日眼地笑，表情诡异又好笑"},
    }
    info = emotion_map.get(name, {
        "emotion": name,
        "tags": [name],
        "description": name
    })
    return {
        "id": f"builtin_{name}",
        "filename": filename,
        "name": name,
        "emotion": info["emotion"],
        "tags": info["tags"],
        "description": info["description"],
        "owner": "builtin"
    }


def get_embedding(api_url: str, api_key: str, model: str, text: str) -> list:
    url = f"{api_url.rstrip('/')}/embeddings"
    payload = json.dumps({
        "model": model,
        "input": text
    }).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {api_key}")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data["data"][0]["embedding"]
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"HTTP Error {e.code}: {body[:200]}")
        raise
    except Exception as e:
        print(f"Request Error: {e}")
        raise


def main():
    parser = argparse.ArgumentParser(description="生成内置表情包的嵌入向量")
    parser.add_argument("--api_url", required=True, help="嵌入API的base URL，如 https://api.openai.com/v1")
    parser.add_argument("--api_key", required=True, help="API密钥")
    parser.add_argument("--model", default="text-embedding-3-small", help="嵌入模型名称")
    args = parser.parse_args()

    if not os.path.isdir(EMOTION_DIR):
        print(f"错误：表情包目录不存在: {EMOTION_DIR}")
        return

    files = [f for f in os.listdir(EMOTION_DIR) if f.lower().endswith(('.jpg', '.jpeg', '.png', '.gif', '.webp'))]
    files.sort()
    if not files:
        print("错误：表情包目录中没有图片文件")
        return

    print(f"找到 {len(files)} 个表情包文件")

    metadata_list = []
    embeddings = []

    for i, filename in enumerate(files):
        info = get_sticker_description(filename)
        search_text = f"{info['description']} {info['emotion']} {' '.join(info['tags'])}"
        print(f"[{i+1}/{len(files)}] 正在计算向量: {info['name']} -> \"{search_text}\"")

        try:
            vec = get_embedding(args.api_url, args.api_key, args.model, search_text)
            embeddings.append(vec)
            info["dim"] = len(vec)
            metadata_list.append(info)
            print(f"  ✓ 维度={len(vec)}")
        except Exception as e:
            print(f"  ✗ 失败: {e}")
            return

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    metadata_path = os.path.join(OUTPUT_DIR, "builtin_metadata.json")
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump({
            "version": 1,
            "model": args.model,
            "dim": len(embeddings[0]) if embeddings else 0,
            "count": len(metadata_list),
            "stickers": metadata_list
        }, f, ensure_ascii=False, indent=2)
    print(f"\n元数据已保存: {metadata_path}")

    bin_path = os.path.join(OUTPUT_DIR, "builtin_embeddings.bin")
    with open(bin_path, "wb") as f:
        dim = len(embeddings[0]) if embeddings else 0
        f.write(struct.pack("<ii", dim, len(embeddings)))
        for vec in embeddings:
            for v in vec:
                f.write(struct.pack("<f", v))
    print(f"向量文件已保存: {bin_path}")

    for filename in files:
        src = os.path.join(EMOTION_DIR, filename)
        dst = os.path.join(OUTPUT_DIR, filename)
        with open(src, "rb") as sf, open(dst, "wb") as df:
            df.write(sf.read())
    print(f"已复制 {len(files)} 个图片到 {OUTPUT_DIR}")

    print(f"\n✅ 完成！共处理 {len(metadata_list)} 个内置表情包")
    print(f"   模型: {args.model}, 维度: {len(embeddings[0]) if embeddings else 0}")


if __name__ == "__main__":
    main()
