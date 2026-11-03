# -*- coding: utf-8 -*-
"""
清除 DeepSeek（大肥鱼）模型的「鼻影」图层。

背景
----
这个模型是 Cubism 5 导出的（moc3 version=5），而 App 的网页运行时是 Cubism 4。
模型里有一层**纯黑半透明**的鼻影贴图（texture_00.png 的 x≈3450-3655, y≈2260-2790），
在 VTube Studio 里靠「正片叠底(Multiply)」渲染成柔和阴影；
在 Cubism 4 运行时里混合模式没生效，被当成普通图层直接盖上 → 鼻子上出现一块黑方块。

处理
----
既然混合模式短期修不了（要换 Cubism 5 运行时），直接把该区域的 alpha 置 0。
模型自带的鼻子绘制在别的贴图区域，不受影响。

用法
----
    python patch_nose_shadow.py <模型目录>

模型目录里应有 textures/texture_00.png。脚本会原地修改（建议先备份）。
"""
import os
import sys

import numpy as np
from PIL import Image

# 鼻影贴图在 texture_00 上的包围盒（已留出羽化边）
BOX = (3450, 2260, 3655, 2790)

def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    tex = os.path.join(sys.argv[1], "textures", "texture_00.png")
    if not os.path.exists(tex):
        print("找不到贴图:", tex)
        return 1
    im = Image.open(tex).convert("RGBA")
    a = np.array(im)
    x0, y0, x1, y1 = BOX
    sub = a[y0:y1, x0:x1]
    touched = int((sub[:, :, 3] > 0).sum())
    sub[:, :, 3] = 0
    a[y0:y1, x0:x1] = sub
    Image.fromarray(a, "RGBA").save(tex, "PNG")
    print("已清除鼻影图层，影响像素 %d" % touched)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
