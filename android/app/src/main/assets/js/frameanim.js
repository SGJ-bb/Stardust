/**
 * PetFrames —— Live2D + 序列帧 混合播放器（可移植版）
 * ---------------------------------------------------------------
 * 设计目标：不假设框架、不假设 PIXI 版本、不依赖 Live2D。
 *   - PIXI 后端：自动识别 PIXI 7 / 8（v8 删了 BaseTexture，这里都兼容）
 *   - Canvas2D 后端：没有 PIXI 的 WebView（老安卓壳、纯前端）也能跑
 *   - 一帧一图集切片，纯 JSON 契约（*.frames.json），见 INTEGRATION.md
 *
 * 用法（PIXI，stradust-pc / stradust-ios 同款）：
 *   import { PetFrames } from './frameanim.js';
 *   const fx = new PetFrames({ app, PIXI });            // app = 已 init 的 PIXI.Application
 *   await fx.load('./frames/hearts.frames.json');
 *   fx.play('hearts', { rect: { x: 100, y: 40, w: 120, h: 120 } });
 *
 * 用法（Canvas2D，无 PIXI）：
 *   const fx = new PetFrames({ canvas: document.querySelector('#fx') });
 *
 * 落位两种姿势：
 *   1) 命中区/任意矩形：rect = {x, y, w, h}（CSS 像素，左上角）
 *   2) 与 Live2D 模型同坐标系（烘焙帧）：rect = PetFrames.rectFromModel(manifest, model)
 *      model = { x, y, scale }  ← 就是 pixi 里 live2dModel.x / .y / .scale.x
 */
(function (global) {
  'use strict';

  const PIXI_MAJOR = (P) => {
    const v = P && P.VERSION ? parseInt(String(P.VERSION), 10) : 0;
    return isNaN(v) ? 0 : v;
  };

  function loadImage(url) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error('image load failed: ' + url));
      img.src = url;
    });
  }

  class PetFrames {
    /**
     * @param {{PIXI?:any, app?:any, canvas?:HTMLCanvasElement, autoTick?:boolean}} opts
     */
    constructor(opts) {
      opts = opts || {};
      this.PIXI = opts.PIXI || global.PIXI || null;
      this.app = opts.app || null;
      // 只给 parent（stage/container）也行：安卓 WebView 里拿不到 Application 实例时用
      this.parent = opts.parent || (this.app && this.app.stage) || null;
      this.canvas = opts.canvas || null;
      this.animations = new Map();
      this.current = null;
      this.frame = 0;
      this.acc = 0;
      this.container = null;
      this.sprite = null;
      this.ctx = null;
      this.alpha = 0;
      this.alphaTarget = 0;
      this.rect = { x: 0, y: 0, w: 0, h: 0 };
      this.onEnd = null;
      this._last = 0;
      this._raf = null;

      this.backend = this.PIXI && this.parent ? 'pixi' : this.canvas ? 'canvas' : 'none';
      if (this.backend === 'pixi') this._initPixi();
      else if (this.backend === 'canvas') this._initCanvas();

      if (this.backend !== 'none' && opts.autoTick !== false) {
        this._loop = this._loop.bind(this);
        this._raf = requestAnimationFrame(this._loop);
      }
    }

    /* ---------------------------------------------------------- 初始化 */
    _initPixi() {
      const P = this.PIXI;
      this.container = new P.Container();
      this.container.visible = false;
      this.container.alpha = 0;
      this.parent.addChild(this.container);
      this.sprite = new P.Sprite();
      this.sprite.anchor.set(0, 0);
      this.container.addChild(this.sprite);
    }

    _initCanvas() {
      this.ctx = this.canvas.getContext('2d');
      this._resize();
      global.addEventListener('resize', () => this._resize());
    }

    _resize() {
      if (!this.canvas) return;
      const dpr = global.devicePixelRatio || 1;
      const r = this.canvas.getBoundingClientRect();
      this.canvas.width = Math.max(1, Math.round(r.width * dpr));
      this.canvas.height = Math.max(1, Math.round(r.height * dpr));
      if (this.ctx) this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    /* ---------------------------------------------------------- 加载 */
    /** @param {string} manifestUrl 相对或绝对路径 */
    async load(manifestUrl) {
      const res = await fetch(manifestUrl);
      if (!res.ok) throw new Error('manifest ' + res.status + ' ' + manifestUrl);
      const meta = await res.json();
      const base = manifestUrl.slice(0, manifestUrl.lastIndexOf('/') + 1);
      const sheetUrl = /^(https?:|data:|blob:)/.test(meta.sheet) ? meta.sheet : base + meta.sheet;
      const img = await loadImage(sheetUrl);

      let textures = null;
      if (this.backend === 'pixi') {
        const P = this.PIXI;
        const tex = P.Texture.from(img);
        // PIXI 7: tex.baseTexture；PIXI 8: tex.source（BaseTexture 已移除）
        const source = tex.source || tex.baseTexture;
        // PIXI 7 的坑：直接 Texture.from(HTMLImageElement) 时 baseTexture 可能还没 valid，
        // 子纹理会被判定为"无效"而不绘制（容器位置/alpha 都对，就是看不见）。
        if (source && source.valid === false) {
          try { source.update(); } catch (e) {}
          if (source.valid === false) {
            await new Promise((res) => {
              const done = () => res();
              source.once ? source.once('loaded', done) : setTimeout(done, 50);
              setTimeout(done, 400);
            });
          }
        }
        const Rect = P.Rectangle;
        textures = [];
        for (let i = 0; i < meta.count; i++) {
          const cx = (i % meta.cols) * meta.frameW;
          const cy = Math.floor(i / meta.cols) * meta.frameH;
          const frame = new Rect(cx, cy, meta.frameW, meta.frameH);
          textures.push(
            PIXI_MAJOR(P) >= 8 ? new P.Texture({ source, frame }) : new P.Texture(source, frame)
          );
        }
      }

      this.animations.set(meta.name, { meta, img, textures });
      return meta;
    }

    async loadAll(urls) {
      const out = [];
      for (const u of urls) {
        try { out.push(await this.load(u)); } catch (e) { console.warn('[PetFrames]', e.message); }
      }
      return out;
    }

    has(name) { return this.animations.has(name); }
    names() { return Array.from(this.animations.keys()); }

    /* ---------------------------------------------------------- 落位 */
    /** 与 Live2D 模型同坐标系（烘焙帧专用） */
    static rectFromModel(meta, model) {
      const r = (meta && meta.rectModel) || [0, 0, meta.frameW, meta.frameH];
      const s = model.scale;
      const k = (meta.pxToModel || 1) ;
      return {
        x: model.x + r[0] * s,
        y: model.y + r[1] * s,
        w: meta.frameW * s * k,
        h: meta.frameH * s * k,
      };
    }

    /**
     * 贴到某个矩形（命中区/头部框），对齐方式由 manifest.anchor 决定。
     * fit='contain'（默认）：整帧缩进 box*k 内，长条特效（爱心 250×449）不会被放大到出屏；
     * fit='width'：只有宽度对齐（老行为，适合宽高比接近的素材）。
     */
    static rectFromAnchorBox(meta, box, scaleK, offset, fit) {
      const k = (scaleK || meta.scaleK || 1);
      let w, h;
      if ((fit || 'contain') === 'width') {
        w = box.w * k;
        h = (w / meta.frameW) * meta.frameH;
      } else {
        const sc = Math.min((box.w * k) / meta.frameW, (box.h * k) / meta.frameH);
        w = meta.frameW * sc;
        h = meta.frameH * sc;
      }
      const off = offset || meta.offset || [0, 0];
      const cx = box.x + box.w * (0.5 + off[0]);
      const cy = box.y + box.h * (0.5 + off[1]);
      const anchor = meta.anchor || [0.5, 0.5];
      return { x: cx - anchor[0] * w, y: cy - anchor[1] * h, w, h };
    }

    /* ---------------------------------------------------------- 播放 */
    /**
     * @param {string} name
     * @param {{rect?:{x,y,w,h}, loop?:boolean, fadeMs?:number, restart?:boolean, onEnd?:Function}} opts
     */
    play(name, opts) {
      opts = opts || {};
      const rec = this.animations.get(name);
      if (!rec) return false;
      const meta = rec.meta;
      if (opts.rect) this.rect = opts.rect;
      this.current = name;
      this.frame = 0;
      this.acc = 0;
      this.loop = opts.loop != null ? opts.loop : !!meta.loop;
      this.onEnd = opts.onEnd || null;
      this.alphaTarget = 1;
      if (this.backend === 'pixi') {
        this.container.visible = true;
        this._applyPixiFrame(rec);
      }
      return true;
    }

    stop(immediate) {
      this.alphaTarget = 0;
      this.current = null;
      if (immediate) {
        this.alpha = 0;
        if (this.backend === 'pixi' && this.container) this.container.visible = false;
        if (this.backend === 'canvas') this._clear();
      }
    }

    get playing() { return !!this.current && this.alphaTarget > 0; }

    /* ---------------------------------------------------------- 每帧 */
    _applyPixiFrame(rec) {
      const idx = Math.min(this.frame, rec.textures.length - 1);
      if (this.sprite.texture !== rec.textures[idx]) this.sprite.texture = rec.textures[idx];
      const meta = rec.meta;
      const sc = this.rect.w / meta.frameW;
      this.container.position.set(this.rect.x, this.rect.y);
      this.container.scale.set(sc);
      this.sprite.width = meta.frameW;
      this.sprite.height = meta.frameH;
    }

    _clear() {
      if (this.ctx) this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }

    _drawCanvas(rec) {
      const { meta, img } = rec;
      const ctx = this.ctx;
      ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
      if (this.alpha <= 0.01) return;
      const idx = Math.min(this.frame, meta.count - 1);
      const sx = (idx % meta.cols) * meta.frameW;
      const sy = Math.floor(idx / meta.cols) * meta.frameH;
      ctx.save();
      ctx.globalAlpha = this.alpha;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(img, sx, sy, meta.frameW, meta.frameH, this.rect.x, this.rect.y, this.rect.w, this.rect.h);
      ctx.restore();
    }

    _loop(now) {
      const dt = this._last ? Math.min(60, now - this._last) : 16;
      this._last = now;
      this._advance(dt);
      this._raf = requestAnimationFrame(this._loop);
    }

    /** 外部如果自己驱动（例如塞进自己的 ticker），调这个即可 */
    tick(dtMs) {
      this._advance(Math.min(60, dtMs || 16));
    }

    _advance(dt) {
      dt = Math.max(1, Math.min(60, dt || 16));   // headless/后台标签页会给到 0 或超大 dt
      const rec = this.current ? this.animations.get(this.current) : null;
      const target = this.alphaTarget;
      this.alpha += (target - this.alpha) * Math.min(1, dt / 110);
      if (Math.abs(target - this.alpha) < 0.01) this.alpha = target;

      if (rec) {
        this.acc += dt;
        const frameMs = 1000 / (rec.meta.fps || 12);
        while (this.acc >= frameMs) {
          this.acc -= frameMs;
          this.frame++;
          if (this.frame >= rec.meta.count) {
            if (this.loop) this.frame = 0;
            else {
              this.frame = rec.meta.count - 1;
              const cb = this.onEnd;
              this.current = null;
              this.alphaTarget = 0;
              if (cb) cb(rec.meta.name, rec.meta);
              break;
            }
          }
        }
      }

      if (this.backend === 'pixi') {
        this.container.alpha = this.alpha;
        if (rec) this._applyPixiFrame(rec);
        if (this.alpha <= 0.01 && !this.current) this.container.visible = false;
      } else if (this.backend === 'canvas') {
        if (rec) this._drawCanvas(rec);
        else if (this.alpha <= 0.01) this._clear();
      }
    }

    destroy() {
      if (this._raf) cancelAnimationFrame(this._raf);
      if (this.backend === 'pixi' && this.container) {
        this.container.destroy({ children: true });
      }
      this.animations.clear();
    }
  }

  global.PetFrames = PetFrames;
  if (typeof module !== 'undefined' && module.exports) module.exports = { PetFrames };
})(typeof window !== 'undefined' ? window : globalThis);
