/**
 * 命中区（PSD 图层当交互区）
 * ---------------------------------------------------------------
 * 数据来自 image2psd2live 的 `tools/pet_zones_gen.py`：
 *   { canvas:[w,h], content_bbox:[x0,y0,x1,y1],
 *     zones:[{ name, kind, group, rect:[x0,y0,x1,y1] }] }
 *
 * 命中规则（与桌宠一致）：
 *   1. 面积小的优先（脸 > 头 > 身体），否则点脸会先命中"头"
 *   2. 只在 content_bbox 内判定，避免空白区乱触发
 *
 * 用法：
 *   const zs = await loadZones(url);
 *   const zone = hitTestByScreen(zs, e.clientX, e.clientY, model.getBounds());
 */

type Any = any;

export interface PetZone {
  name: string;
  kind: string;
  group?: string;
  rect: [number, number, number, number];
}

export interface PetZoneSet {
  canvas: [number, number];
  contentBbox?: [number, number, number, number];
  zones: PetZone[];
}

export async function loadZones(url: string): Promise<PetZoneSet | null> {
  try {
    const res = await fetch(url);
    if (!res.ok) return null;
    const j = (await res.json()) as Any;
    if (!j || !Array.isArray(j.zones)) return null;
    return {
      canvas: (j.canvas as [number, number]) || [1024, 1024],
      contentBbox: j.content_bbox || j.contentBbox,
      zones: j.zones.filter((z: Any) => Array.isArray(z.rect)),
    };
  } catch (e) {
    console.warn("命中区加载失败:", e);
    return null;
  }
}

/** 把画布归一化坐标 (u,v ∈ 0..1) 映射到模型坐标后做命中 */
export function hitTestNormalized(zs: PetZoneSet, u: number, v: number): PetZone | null {
  const [w, h] = zs.canvas || [1024, 1024];
  const x = u * w;
  const y = v * h;

  if (zs.contentBbox) {
    const [bx0, by0, bx1, by1] = zs.contentBbox;
    if (x < bx0 - 4 || x > bx1 + 4 || y < by0 - 4 || y > by1 + 4) return null;
  }

  let best: PetZone | null = null;
  let bestArea = Infinity;
  for (const z of zs.zones) {
    const [x0, y0, x1, y1] = z.rect;
    if (x < x0 || x > x1 || y < y0 || y > y1) continue;
    const area = Math.max(1, (x1 - x0) * (y1 - y0));
    if (area < bestArea) {
      bestArea = area;
      best = z;
    }
  }
  return best;
}

/**
 * 屏幕坐标 → 命中区
 * @param bounds 模型在屏幕上的包围盒（model.getBounds()）
 */
export function hitTestByScreen(
  zs: PetZoneSet,
  clientX: number,
  clientY: number,
  bounds: { x: number; y: number; width: number; height: number } | null,
): PetZone | null {
  if (!bounds || bounds.width <= 0 || bounds.height <= 0) return null;
  const u = (clientX - bounds.x) / bounds.width;
  const v = (clientY - bounds.y) / bounds.height;
  if (u < 0 || u > 1 || v < 0 || v > 1) return null;
  return hitTestNormalized(zs, u, v);
}

/** 部位 → 默认反馈（表情 / 动作 / 特效），可按业务改 */
export const DEFAULT_ZONE_REACTION: Record<
  string,
  { frames?: string; expression?: string; motion?: string }
> = {
  head: { frames: "hearts", expression: "Love" },
  face: { frames: "sparkle", expression: "Love" },
  body: { motion: "Nod" },
  hand: { frames: "sparkle" },
  hair: { frames: "sparkle" },
};
