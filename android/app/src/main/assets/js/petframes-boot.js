/**
 * petframes-boot.js —— Android WebView 端的零侵入接入层
 * ---------------------------------------------------------------
 * 背景：stradust 安卓端的 Live2D 页面是 Kotlin 里 `buildLive2DHtml()` 拼出来的内联 HTML，
 * `app` / `model` 都是内联脚本里的局部变量，外部脚本拿不到。
 *
 * 所以这里不改内联脚本，只做两件事：
 *   1) 包一层 PIXI.live2d.Live2DModel.from —— 模型一加载完就拿到实例；
 *   2) 等模型挂到 stage 上（有 parent）后，用 parent 建帧动画层（不需要 Application 实例）。
 *
 * 加载顺序（必须）：
 *   pixi.min.js → pixi-live2d-display.min.js → live2dcubismcore.min.js
 *   → frameanim.js → petframes-boot.js → <内联脚本>
 *
 * 对外 API（Kotlin 用 evaluateJavascript 调）：
 *   window.playPetFrames('hearts')                 // overlay 特效
 *   window.playPetFrames('greet', 'replace')       // 替身，模型淡出
 *   window.stopPetFrames()                         // 停止并复原
 *   window.petFramesInfo()                         // { ready, list: [...], model: bool }
 *   window.playPetFramesRect('hearts', {x,y,w,h})  // 精确指定位置（CSS 像素）
 */
(function () {
  'use strict';
  var P = window.PIXI;
  if (!P || !P.live2d || !P.live2d.Live2DModel) {
    console.warn('[petfx] PIXI.live2d 未就绪，跳过帧动画层');
    return;
  }

  // 用本脚本自己的 URL 推导目录：http://127.0.0.1:PORT/js/petframes-boot.js → .../js/
  // 兼容 file:// 回退（assets 被拷到别处时也能找到 frames）
  var SELF = (document.currentScript && document.currentScript.src) || '';
  var BASE = SELF
    ? SELF.replace(/petframes-boot\.js(\?.*)?$/, '') + 'petframes/'
    : 'js/petframes/';
  var INDEX = ['hearts', 'zzz', 'sparkle', 'greet'];
  var fx = null;
  var model = null;
  var ready = false;
  var pending = [];                            // 模型没就绪时先缓存播放请求

  function whenParent(m, cb) {
    var tries = 0;
    (function tick() {
      if (m.parent) return cb(m.parent);
      if (++tries > 120) return;               // ~6s 后放弃
      setTimeout(tick, 50);
    })();
  }

  function boot(m) {
    if (m === model) return;
    model = m;
    window.__petfxModel = m;
    whenParent(m, function (stage) {
      if (fx) return;
      try {
        fx = new PetFrames({ PIXI: P, parent: stage });
        window.__fx = fx;                       // 调试/自测用
        var urls = INDEX.map(function (n) { return BASE + n + '.frames.json'; });
        fx.loadAll(urls).then(function () {
          ready = true;
          window.__petfxReady = true;
          console.log('[petfx] ready: ' + fx.names().join(','));
          var q = pending.splice(0);
          for (var i = 0; i < q.length; i++) {
            try { window.playPetFrames(q[i][0], q[i][1]); } catch (e) {}
          }
        }).catch(function (e) {
          console.warn('[petfx] load fail: ' + (e && e.message));
        });
      } catch (e) {
        console.warn('[petfx] init fail: ' + (e && e.message));
      }
    });
  }

  // ---- 1) 包一层 from，模型加载完自动接上
  var of = P.live2d.Live2DModel.from;
  if (of && !of.__petfxWrapped) {
    var wrapped = function () {
      var p = of.apply(this, arguments);
      if (p && p.then) return p.then(function (m) { try { boot(m); } catch (e) {} return m; });
      return p;
    };
    wrapped.__petfxWrapped = true;
    P.live2d.Live2DModel.from = wrapped;
  }

  // ---- 2) 位置计算：模型 bbox 的相对区域（命中区在 WebView 里没有 zones.json，用比例）
  function modelBox() {
    var s = model.scale.x;
    var w = model.width * s;
    var h = model.height * s;
    return { x: model.x, y: model.y, w: w, h: h };
  }

  function boxFor(attach) {
    var b = modelBox();
    if (attach === 'head') {
      return { x: b.x + b.w * 0.18, y: b.y + b.h * 0.02, w: b.w * 0.64, h: b.h * 0.30 };
    }
    if (attach === 'hand') {
      return { x: b.x + b.w * 0.30, y: b.y + b.h * 0.30, w: b.w * 0.40, h: b.h * 0.30 };
    }
    return { x: b.x + b.w * 0.10, y: b.y + b.h * 0.25, w: b.w * 0.80, h: b.h * 0.55 };
  }

  function rectFor(meta, explicit) {
    if (explicit) return explicit;
    if (meta.space === 'model' && meta.rectModel) {
      return PetFrames.rectFromModel(meta, { x: model.x, y: model.y, scale: model.scale.x });
    }
    return PetFrames.rectFromAnchorBox(meta, boxFor(meta.attach || 'head'), meta.scaleK || 1);
  }

  // ---- 3) 对外 API
  window.playPetFrames = function (name, mode, explicitRect) {
    if (!name) return false;
    if (!fx || !ready) { pending.push([name, mode]); return false; }
    if (!fx.has(name)) return false;
    var meta = fx.animations.get(name).meta;
    var blend = mode || meta.blend || 'overlay';
    var rect = rectFor(meta, explicitRect);

    if (blend === 'replace') {
      // pixi-live2d-display 的 Live2DModel.render 不走 PIXI 的 worldAlpha，设 alpha=0 不会隐藏，
      // 所以这里靠 visible：帧先淡入，~130ms 后把模型整只关掉，播完再打开。
      fx.onEnd = function () { if (model) model.visible = true; };
      fx.play(name, { rect: rect });
      setTimeout(function () { if (model && fx.playing) model.visible = false; }, 130);
      return true;
    }
    fx.play(name, { rect: rect });
    return true;
  };

  window.playPetFramesRect = function (name, rect) {
    return window.playPetFrames(name, '', rect);
  };

  window.stopPetFrames = function () {
    if (fx) fx.stop();
    if (model) model.visible = true;
    pending.length = 0;
  };

  window.petFramesInfo = function () {
    return {
      ready: ready,
      list: fx ? fx.names() : [],
      model: !!model,
      playing: fx ? !!fx.current : false,
    };
  };

  console.log('[petfx] boot installed');
})();
