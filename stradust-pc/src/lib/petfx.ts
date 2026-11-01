/**
 * 帧动画层（PC / Tauri 端）
 * ---------------------------------------------------------------
 * Live2D 管呼吸/眨眼/视线/口型，序列帧管一次性动作与特效。
 * 这一层不碰 Live2D 内部：只往同一个 stage 上叠一个容器，按模型 bbox 算落位。
 *
 * 用法（Live2DCanvas 里模型加载完后一行）：
 *   await initPetFx(app, loadedModel)
 * 触发：
 *   playPetFrames('hearts')            // overlay 特效
 *   playPetFrames('greet','replace')   // 替身
 *   stopPetFrames()
 * 调试台（仅 dev）：页面左下角会自动出现 4 个按钮，或控制台 window.petfx
 */
import "./petframes/frameanim.js";

type Any = any;

let fx: Any = null;
let model: Any = null;
let ready = false;
let pending: Array<[string, string]> = [];

const INDEX = [
  "/frames/hearts.frames.json",
  "/frames/zzz.frames.json",
  "/frames/sparkle.frames.json",
  "/frames/greet.frames.json",
];

function PetFramesClass(): Any {
  return (window as Any).PetFrames;
}

/** 模型 bbox 里的相对区域：Web 端没有 zones.json，用比例近似命中区 */
function boxFor(attach: string) {
  const s = model.scale.x;
  const w = model.width * s;
  const h = model.height * s;
  const b = { x: model.x, y: model.y, w, h };
  if (attach === "head") {
    return { x: b.x + b.w * 0.18, y: b.y + b.h * 0.02, w: b.w * 0.64, h: b.h * 0.30 };
  }
  if (attach === "hand") {
    return { x: b.x + b.w * 0.30, y: b.y + b.h * 0.30, w: b.w * 0.40, h: b.h * 0.30 };
  }
  return { x: b.x + b.w * 0.10, y: b.y + b.h * 0.25, w: b.w * 0.80, h: b.h * 0.55 };
}

export async function initPetFx(app: Any, loadedModel: Any) {
  model = loadedModel;
  if (fx) return fx;
  const PetFrames = PetFramesClass();
  if (!PetFrames) {
    console.warn("[petfx] PetFrames 未加载");
    return null;
  }
  const PIXI = await import("pixi.js");
  fx = new PetFrames({ PIXI, app });
  (window as Any).petfx = fx;

  try {
    await fx.loadAll(INDEX);
    ready = true;
    (window as Any).__petfxReady = true;
    console.log("[petfx] ready: " + fx.names().join(","));
    const q = pending.splice(0);
    q.forEach(([n, m]) => playPetFrames(n, m));
    if ((import.meta as Any).env?.DEV) mountDevBar();
  } catch (e: Any) {
    console.warn("[petfx] load fail: " + (e?.message || e));
  }
  return fx;
}

export function playPetFrames(name: string, mode?: string, rect?: Any) {
  if (!fx || !ready) {
    pending.push([name, mode || ""]);
    return false;
  }
  if (!fx.has(name) || !model) return false;
  const meta = fx.animations.get(name).meta;
  const blend = mode || meta.blend || "overlay";

  const r =
    rect ||
    (meta.space === "model" && meta.rectModel
      ? PetFramesClass().rectFromModel(meta, { x: model.x, y: model.y, scale: model.scale.x })
      : PetFramesClass().rectFromAnchorBox(meta, boxFor(meta.attach || "head"), meta.scaleK || 1));

  if (blend === "replace") {
    // pixi-live2d-display 的 Live2DModel 不吃 PIXI 的 alpha，用 visible 切换
    fx.onEnd = () => { if (model) model.visible = true; };
    fx.play(name, { rect: r });
    setTimeout(() => { if (model && fx?.playing) model.visible = false; }, 130);
    return true;
  }
  fx.play(name, { rect: r });
  return true;
}

export function stopPetFrames() {
  if (fx) fx.stop();
  if (model) model.visible = true;
  pending = [];
}

export function petFramesInfo() {
  return { ready, list: fx ? fx.names() : [], playing: !!fx?.current };
}

/** 仅开发环境：左下角小工具条，方便直接点着测 */
function mountDevBar() {
  if (document.getElementById("petfx-devbar")) return;
  const bar = document.createElement("div");
  bar.id = "petfx-devbar";
  bar.style.cssText = [
    "position:fixed", "left:12px", "bottom:12px", "z-index:2147483000",
    "display:flex", "gap:6px", "padding:8px", "border-radius:10px",
    "background:rgba(20,22,28,.86)", "backdrop-filter:blur(8px)",
    "border:1px solid rgba(255,255,255,.12)", "font:12px/1 system-ui",
  ].join(";");
  const mk = (label: string, fn: () => void) => {
    const b = document.createElement("button");
    b.textContent = label;
    b.style.cssText =
      "padding:6px 9px;border-radius:7px;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:#e6e8ef;cursor:pointer;font-size:12px";
    b.onmouseenter = () => (b.style.background = "rgba(122,208,168,.22)");
    b.onmouseleave = () => (b.style.background = "rgba(255,255,255,.06)");
    b.onclick = fn;
    bar.appendChild(b);
  };
  mk("爱心", () => playPetFrames("hearts"));
  mk("星星", () => playPetFrames("sparkle"));
  mk("ZZZ", () => playPetFrames("zzz"));
  mk("打招呼", () => playPetFrames("greet", "replace"));
  mk("停止", () => stopPetFrames());
  document.body.appendChild(bar);
}
