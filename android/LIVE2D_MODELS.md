# Live2D 模型清单与致谢

## DeepSeek（大肥鱼）

- 位置：`/storage/emulated/0/Android/data/com.aicompanion/files/live2d_models/deepseek/`
- 来源：VTube Studio StreamingAssets 里的 `DeepSeek` 模型包（moc3 + 5 张 4096 贴图 + physics3）
- **感谢 B 站大佬神宫凉子大佬的开源模型。**
- 致谢文案写在 `DeepSeek.model3.json` 的 `Description` 字段，App 的「设置 → Live2D 模型」列表会直接显示在模型名下面。

### 落地时做的三件事

1. **文件名全部改成 ASCII**（`model.moc3` / `textures/texture_0N.png` / `motions/*.motion3.json`）。
   原因：中文文件名会让 `adb push` 直接崩溃（`std::bad_alloc`），后续读写也容易踩编码坑。
2. **把待机动作接进 model3.json**。原包是 VTube Studio 的格式，`Animation/IDLE.motion3.json`
   并没有被 `FileReferences.Motions` 引用，App 这边就不会播。现在显式写进 `Motions.Idle`，
   另外三个手势动作放进 `Motions.TapBody`。
3. **补待机循环**。pixi-live2d-display 不会自己循环待机动作，模型加载完只会眨眼+呼吸。
   `Live2DWebView` 里新增 `startIdleLoop()`：每 9 秒检查一次，若 6 秒内没播过别的动作，
   就随机播一条 `Idle` 组动作（表情/点按动作会通过 `_lastMotionAt` 让它让路）。

### 换模型时注意

- 模型目录放在 `live2d_models/<id>/`，里面必须有一个 `*.model3.json`。
- 列表存在 `shared_prefs/model_manager.xml` 的 `models_list`，激活项写在 `app_prefs.xml` 的
  `active_model_path`；两者不一致时会加载不到。

### 已知问题：鼻子上有一块黑

这个模型的鼻影是**纯黑半透明**贴图（`texture_00.png` 上 x≈3450-3655 / y≈2260-2790 的一块羽化长条），
在 VTube Studio 里靠「正片叠底」压成柔和阴影；而我们的运行时是 Cubism 4，混合模式没生效，
被当普通图层盖上 → 鼻子出现黑方块。

处理：`python android/tools/patch_nose_shadow.py <模型目录>`（把该区域 alpha 置 0）。
贴图改完后要删掉 `files/live2d_cache/<模型id>/` 让 App 重新拷贝，否则会继续用旧缓存。

> 根治需要升级运行时到 Cubism 5（pixi-live2d-display v5 分支 + pixi v8），
> 或者请作者用 Cubism Editor 按 Cubism 4.2 / SDK for Web 重新导出。

### 鼻影黑块：真正的根因（2026-11-03 定位）

前面记的「正片叠底没生效」是错的。真实原因是**遮罩数量超限**：

- 这个模型用了 **49 个剪裁遮罩**，单个部件最多同时叠 **13 个**
- Cubism Web 框架单张遮罩纹理上限 **36 个**（9 区域 × 4 通道，`ClippingMaskMaxCountOnDefault`）
- 超限后框架降级成「每次清空重画一个遮罩」的分支，遮罩全部错乱
- 结果就是本该被裁掉的鼻影层整块露出来 —— C4 下是黑块，C5 下是白块

**为什么 VTS 里没问题**：VTS 用的是 Cubism SDK for **Unity**。官方文档《各 Cubism SDK 的比较》写明：
Unity 与 Native/Web 的遮罩生成方式不同（Web 会生成「包含添加蒙版一侧的矩形」），
**「在大的图形网格被小遮罩覆盖的模型中，蒙版区域可能被压碎」**。Unity 版没有 36 个的上限。

**真正的修复**（已在 Cubism 5 运行时上验证有效）：

```js
// Cubism5InternalModel
this.renderer.initialize(this.coreModel, 2);   // 默认 1 张遮罩纹理 → 给 2 张
```

2 张 × 32 = 64 > 49，遮罩不再降级。实测鼻子干净，眼睛/嘴的颜色也一并修正了
（之前遮罩错乱影响的不止鼻子）。

**但 C4 框架做不到**：App 里的 `pixi-live2d-display.min.js` 中
`_renderTextureCount` 和 `ClippingMaskMaxCountOnMultiRenderTexture` 这两个符号**根本不存在**，
即 Cubism 4 Web 框架不支持多张遮罩纹理，传参会被忽略。

> 结论：**根治必须升级到 Cubism 5 框架**（pixi-live2d-display v5 / naari3 fork + pixi 8）。
> 在那之前，`tools/patch_nose_shadow.py` 的贴图级修补是唯一可用方案。
