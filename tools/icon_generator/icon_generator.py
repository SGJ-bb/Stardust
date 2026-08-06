#!/usr/bin/env python3
"""
图标批量生成工具 — Icon Batch Generator
========================================
支持 OpenAI 兼容的图片生成 API（国内中转 / DALL-E 3 / SDXL 等）
批量生成 App 图标，输出 PNG + SVG 双格式

用法:
    python icon_generator.py                    # 生成全部图标
    python icon_generator.py --category core     # 只生成核心入口图标
    python icon_generator.py --id 1 2 3          # 只生成指定 ID
    python icon_generator.py --list              # 列出所有图标
    python icon_generator.py --dry-run           # 预览不执行
"""

import argparse
import base64
import json
import os
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import Optional

# ============================================================
# 配置加载
# ============================================================

CONFIG_PATH = Path(__file__).parent / ".env"
PROMPTS_PATH = Path(__file__).parent / "icon_prompts.json"


def load_config() -> dict:
    """从 .env 文件加载配置"""
    config = {
        "api_base": "https://api.openai.com/v1",
        "api_key": "",
        "model": "dall-e-3",
        "size": "1024x1024",
        "quality": "standard",
        "style": "vivid",
        "output_dir": "./generated_icons",
        "concurrency": 2,
        "max_retries": 3,
        "retry_delay": 5,
        "svg_converter": "none",
        "proxy": None,
    }

    if CONFIG_PATH.exists():
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, value = line.partition("=")
                key = key.strip().lower()
                value = value.strip()
                # 去除引号
                if (value.startswith('"') and value.endswith('"')) or \
                   (value.startswith("'") and value.endswith("'")):
                    value = value[1:-1]
                # 映射到配置键
                mapping = {
                    "image_api_base": "api_base",
                    "image_api_key": "api_key",
                    "image_model": "model",
                    "image_size": "size",
                    "image_quality": "quality",
                    "image_style": "style",
                    "output_dir": "output_dir",
                    "concurrency": "concurrency",
                    "max_retries": "max_retries",
                    "retry_delay": "retry_delay",
                    "svg_converter": "svg_converter",
                    "http_proxy": "proxy",
                    "https_proxy": "proxy",
                }
                if key in mapping:
                    val = value
                    if key in ("concurrency", "max_retries", "retry_delay"):
                        val = int(value)
                    config[mapping[key]] = val

    return config


def load_prompts() -> list[dict]:
    """加载提示词数据"""
    if not PROMPTS_PATH.exists():
        print(f"[错误] 提示词文件不存在: {PROMPTS_PATH}")
        print("请先运行一次初始化或手动创建 icon_prompts.json")
        sys.exit(1)
    with open(PROMPTS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


# ============================================================
# 数据模型
# ============================================================

@dataclass
class IconItem:
    id: int
    name: str
    emoji: str
    category: str
    usage: str
    prompt_en: str
    prompt_zh: str = ""
    filename_base: str = ""
    priority: int = 0  # 越小越优先

    def __post_init__(self):
        if not self.filename_base:
            self.filename_base = f"{self.id:02d}_{self.name}"


@dataclass
class GenerationResult:
    item: IconItem
    success: bool
    png_path: Optional[str] = None
    svg_path: Optional[str] = None
    error: str = ""
    duration_ms: int = 0


# ============================================================
# API 调用
# ============================================================

class ImageGenerator:
    """图片生成器 — 兼容 OpenAI images API 格式"""

    def __init__(self, config: dict):
        self.config = config
        self.api_base = config["api_base"].rstrip("/")
        self.api_key = config["api_key"]
        self.model = config["model"]
        self.size = config["size"]
        self.quality = config.get("quality", "standard")
        self.style = config.get("style", "vivid")
        self.max_retries = config["max_retries"]
        self.retry_delay = config["retry_delay"]
        self.proxy = config.get("proxy")

        if not self.api_key:
            print("[错误] 未配置 API Key！")
            print(f"请编辑 {CONFIG_PATH} 并填入 IMAGE_API_KEY")
            sys.exit(1)

    def _build_prompt(self, item: IconItem) -> str:
        """构建最终发送给 API 的提示词"""
        # 组合中英文提示词以获得最佳效果
        parts = []
        if item.prompt_zh:
            parts.append(item.prompt_zh)
        parts.append(item.prompt_en)
        # 添加统一风格后缀
        style_suffix = (
            ", app icon design system, consistent art style across all icons, "
            "rounded corners, subtle gradient, soft shadow, clean modern UI, "
            "transparent background, high quality, professional"
        )
        return "\n".join(parts) + style_suffix

    def _make_request(self, prompt: str) -> tuple[Optional[bytes], str]:
        """调用图片生成 API，返回 (图片数据/None, 错误信息)"""
        url = f"{self.api_base}/images/generations"
        payload = json.dumps({
            "model": self.model,
            "prompt": prompt,
            "n": 1,
            "size": self.size,
            **({"quality": self.quality} if self.model == "dall-e-3" else {}),
            **({"style": self.style} if self.model == "dall-e-3" else {}),
        }).encode("utf-8")

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}",
        }

        req = urllib.request.Request(url, data=payload, headers=headers, method="POST")

        if self.proxy:
            proxy_handler = urllib.request.ProxyHandler({
                "http": self.proxy,
                "https": self.proxy,
            })
            opener = urllib.request.build_opener(proxy_handler)
        else:
            opener = urllib.request.build_opener()

        try:
            with opener.open(req, timeout=120) as resp:
                result = json.loads(resp.read().decode("utf-8"))

                # 解析响应 — 兼容多种格式
                image_data = None
                if "data" in result and len(result["data"]) > 0:
                    img_item = result["data"][0]

                    # b64_json 格式 (DALL-E 3)
                    if "b64_json" in img_item:
                        image_data = base64.b64decode(img_item["b64_json"])
                    # url 格式 (SDXL 等)
                    elif "url" in img_item:
                        img_url = img_item["url"]
                        try:
                            with urllib.request.urlopen(img_url, timeout=60) as img_resp:
                                image_data = img_resp.read()
                        except Exception as e:
                            return None, f"下载图片失败: {e}"

                if image_data:
                    return image_data, ""
                return None, f"API 返回无图片数据: {json.dumps(result)[:200]}"

        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")[:500]
            return None, f"HTTP {e.code}: {body}"
        except urllib.error.URLError as e:
            return None, f"网络错误: {e.reason}"
        except Exception as e:
            return None, f"请求异常: {e}"

    def generate(self, item: IconItem, output_dir_png: Path, output_dir_svg: Path) -> GenerationResult:
        """生成单个图标（含重试）"""
        start = time.time()
        prompt = self._build_prompt(item)

        print(f"  [{item.id:02d}] {item.emoji} {item.name}...", end=" ", flush=True)

        last_error = ""
        for attempt in range(1, self.max_retries + 1):
            if attempt > 1:
                print(f"\n  [重试 {attempt}/{self.max_retries}] 等待 {self.retry_delay}s...", end=" ", flush=True)
                time.sleep(self.retry_delay)

            image_data, error = self._make_request(prompt)

            if image_data:
                # 保存 PNG
                png_path = output_dir_png / f"{item.filename_base}.png"
                svg_path = output_dir_svg / f"{item.filename_base}.svg"

                with open(png_path, "wb") as f:
                    f.write(image_data)

                duration = int((time.time() - start) * 1000)
                size_kb = len(image_data) // 1024

                print(f"OK ({size_kb}KB, {duration}ms)")

                result = GenerationResult(
                    item=item,
                    success=True,
                    png_path=str(png_path),
                    duration_ms=duration,
                )

                # 尝试转换 SVG
                if self.config.get("svg_converter") != "none":
                    svg_p = self._convert_to_svg(png_path, svg_path)
                    if svg_p:
                        result.svg_path = str(svg_p)

                return result

            last_error = error
            print(f"FAIL ({error[:60]})", end="", flush=True)

        duration = int((time.time() - start) * 1000)
        print(f"\n  [最终失败] {last_error[:100]}")
        return GenerationResult(item=item, success=False, error=last_error, duration_ms=duration)

    def _convert_to_svg(self, png_path: Path, svg_path: Path) -> Optional[str]:
        """PNG 转 SVG（需要 vtracer 或 potrace）"""
        converter = self.config.get("svg_converter", "none")
        try:
            if converter == "vtracer":
                import subprocess
                subprocess.run([
                    sys.executable, "-m", "vtracer",
                    "--str", str(png_path), "--output", str(svg_path)
                ], check=True, capture_output=True)
                return str(svg_path)
            elif converter == "potrace":
                import subprocess
                # 先转 PNM 再用 potrace 转 SVG
                pnm_path = png_path.with_suffix(".pnm")
                subprocess.run(["convert", str(png_path), str(pnm_path)], check=True, capture_output=True)
                subprocess.run(["potrace", "-s", "-o", str(svg_path), str(pnm_path)], check=True, capture_output=True)
                pnm_path.unlink(missing_ok=True)
                return str(svg_path)
        except FileNotFoundError:
            print(f"    [警告] {converter} 未安装，跳过 SVG 转换")
        except Exception as e:
            print(f"    [警告] SVG 转换失败: {e}")
        return None


# ============================================================
# 主流程
# ============================================================

def ensure_dirs(base_dir: str) -> tuple[Path, Path]:
    """确保输出目录存在"""
    png_dir = Path(base_dir) / "png"
    svg_dir = Path(base_dir) / "svg"
    png_dir.mkdir(parents=True, exist_ok=True)
    svg_dir.mkdir(parents=True, exist_ok=True)
    return png_dir, svg_dir


def list_icons(items: list[IconItem]):
    """列出所有图标"""
    categories = {}
    for item in items:
        categories.setdefault(item.category, []).append(item)

    print(f"\n{'='*60}")
    print(f"  共 {len(items)} 个图标待生成\n")

    for cat_name, cat_items in sorted(categories.items()):
        print(f"  【{cat_name}】({len(cat_items)}个)")
        for it in cat_items:
            status = "已存在" if _icon_exists(it, Path("./generated_icons")) else "待生成"
            print(f"    {it.id:02d}. {it.emoji} {it.name:<12} — {status}")
        print()


def _icon_exists(item: IconItem, base_dir: Path) -> bool:
    png_path = base_dir / "png" / f"{item.filename_base}.png"
    return png_path.exists()


def run_generation(config: dict, items: list[IconItem], skip_existing: bool = True) -> list[GenerationResult]:
    """执行批量生成"""
    generator = ImageGenerator(config)
    png_dir, svg_dir = ensure_dirs(config["output_dir"])

    # 过滤
    if skip_existing:
        pending = [it for it in items if not _icon_exists(it, Path(config["output_dir"]))]
        skipped = len(items) - len(pending)
        if skipped > 0:
            print(f"[信息] 跳过已存在的 {skipped} 个图标")
    else:
        pending = items

    if not pending:
        print("[完成] 所有图标已生成完毕！")
        return []

    print(f"\n{'='*60}")
    print(f"  图标批量生成工具")
    print(f"  模型: {config['model']} | 尺寸: {config['size']}")
    print(f"  待生成: {len(pending)} 个 | 并发: {config['concurrency']}")
    print(f"  输出: {Path(config['output_dir']).resolve()}")
    print(f"{'='*60}\n")

    results: list[GenerationResult] = []
    concurrency = min(config["concurrency"], len(pending))

    with ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = {
            executor.submit(generator.generate, it, png_dir, svg_dir): it
            for it in pending
        }
        for future in as_completed(futures):
            result = future.result()
            results.append(result)

    # 统计
    success_count = sum(1 for r in results if r.success)
    fail_count = sum(1 for r in results if not r.success)
    total_time = sum(r.duration_ms for r in results) // 1000

    print(f"\n{'='*60}")
    print(f"  完成！成功: {success_count} | 失败: {fail_count} | 总耗时: {total_time}s")

    if fail_count > 0:
        print(f"\n  失败列表:")
        for r in results:
            if not r.success:
                print(f"    [{r.item.id:02d}] {r.item.name}: {r.error[:80]}")

    # 生成报告
    report_path = Path(config["output_dir"]) / "_generation_report.json"
    report_data = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "model": config["model"],
        "total": len(pending),
        "success": success_count,
        "failed": fail_count,
        "results": [
            {
                "id": r.item.id,
                "name": r.item.name,
                "emoji": r.item.emoji,
                "success": r.success,
                "png": r.png_path,
                "svg": r.svg_path,
                "error": r.error,
                "duration_ms": r.duration_ms,
            }
            for r in results
        ],
    }
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report_data, f, ensure_ascii=False, indent=2)
    print(f"  报告已保存: {report_path}")

    return results


# ============================================================
# CLI 入口
# ============================================================

CATEGORY_MAP = {
    "core": "核心功能入口",
    "chat": "聊天交互",
    "profile": "个人资料统计",
    "toolbar": "页面标题栏",
    "virtual_world": "虚拟世界模块",
    "achievement": "成就系统徽章",
    "functional": "其他功能性图标",
}


def main():
    parser = argparse.ArgumentParser(
        description="图标批量生成工具 — 批量调用 AI 图片 API 生成 App 图标",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python icon_generator.py                  # 生成全部
  python icon_generator.py --category core   # 只生成核心入口
  python icon_generator.py --id 1 2 3       # 生成指定ID
  python icon_generator.py --list           # 列出所有图标
  python icon_generator.py --dry-run        # 预览要生成的列表
  python icon_generator.py --force          # 强制重新生成已有图标
        """,
    )
    parser.add_argument("--category", "-c", choices=list(CATEGORY_MAP.keys()),
                        help="按分类生成")
    parser.add_argument("--id", nargs="+", type=int,
                        help="指定图标 ID 生成")
    parser.add_argument("--list", "-l", action="store_true",
                        help="列出所有图标")
    parser.add_argument("--dry-run", action="store_true",
                        help="预览模式，不实际生成")
    parser.add_argument("--force", "-f", action="store_true",
                        help="强制重新生成已有的图标")
    args = parser.parse_args()

    # 加载数据
    config = load_config()
    raw_prompts = load_prompts()

    # 转换为 IconItem 对象
    all_items = [
        IconItem(
            id=p["id"],
            name=p["name"],
            emoji=p["emoji"],
            category=p["category"],
            usage=p["usage"],
            prompt_en=p["prompt_en"],
            prompt_zh=p.get("prompt_zh", ""),
            filename_base=p.get("filename_base", ""),
            priority=p.get("priority", 0),
        )
        for p in raw_prompts
    ]

    # 列表模式
    if args.list:
        list_icons(all_items)
        return

    # 过滤
    selected = all_items
    if args.category:
        selected = [it for it in all_items if it.category == args.category]
        cat_label = CATEGORY_MAP.get(args.category, args.category)
        print(f"[筛选] 分类: {cat_label} ({len(selected)}个)")
    if args.id:
        id_set = set(args.id)
        selected = [it for it in selected if it.id in id_set]
        print(f"[筛选] 指定ID: {args.id} ({len(selected)}个)")

    if not selected:
        print("[错误] 没有匹配的图标")
        return

    # 预览模式
    if args.dry_run:
        print(f"\n[预览] 将生成以下 {len(selected)} 个图标:\n")
        for it in selected:
            print(f"  {it.id:02d}. {it.emoji} {it.name} ({it.category}) — {it.usage}")
        return

    # 执行生成
    run_generation(config, selected, skip_existing=not args.force)


if __name__ == "__main__":
    main()
