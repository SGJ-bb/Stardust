# 语音唤醒（离线关键词检测）

> 目标：手机息屏放桌上，说一句「星尘星尘」就能把它叫起来，然后直接说话；全程离线，不联网、不上传音频。

## 1. 实现选型

| 方案 | 判断 |
|---|---|
| **sherpa-onnx KWS**（本项目采用） | 中文 zipformer 3.3M int8 模型（约 5MB），建模单元是「声母 + 带调韵母」，**任意中文唤醒词**都能拼出来；纯离线、CPU 占用低（单核 <10%）；Apache-2.0 |
| Android `SpeechRecognizer` 轮询 | 要联网、功耗高、识别结果不稳定，且国产 ROM 上常常没有可用服务 |
| Porcupine | 效果好但需 AccessKey / 商业授权，中文自定义词要额外训练 |
| Vosk | 也能做关键词，但中文模型 40MB+，比 KWS 重 8 倍 |

依赖直接从 Maven Central 拉，**不需要下载 GitHub 的模型包**：

```gradle
implementation 'com.bihe0832.android:lib-sherpa-onnx:6.25.21'       // JNI：arm64-v8a / armeabi-v7a / x86 / x86_64
implementation 'com.bihe0832.android:lib-sherpa-kws-model:6.16.7'   // 模型以 assets 形式自动合并进 APK

android {
    packaging {
        jniLibs {
            // sherpa 的 lib-onnx 与 onnxruntime-android 都带 libonnxruntime.so，是两套不同构建
            pickFirsts += ['lib/**/libonnxruntime.so']
        }
    }
}
```

> ⚠️ **必须留下 sherpa 那份 `libonnxruntime.so`**。工程里原本的 `onnxruntime-android:1.17.0` 也提供同名文件，
> 但两者大小与导出符号不同；用错的那份会在 `dlopen` 时报
> `cannot locate symbol "OrtGetApiBase" referenced by libsherpa-onnx-jni.so`。
> 现在的合并顺序里 `lib-onnx`（sherpa 的）在前，`pickFirsts` 会留下它（可用 `unzip -l` 核对 apk 里
> `lib/x86_64/libonnxruntime.so` 是否为 17800896 字节 / md5 `deca7682…`）。

## 2. 唤醒词怎么写

设置 → 语音设置 → **语音唤醒（离线）**：

- 中文直接写：`星尘星尘,你好星尘`。运行时自动转拼音，界面会显示「识别用拼音：x īng ch én x īng ch én」。
- 多音字想纠正时可以直接写拼音：`xing1 chen2 xing1 chen2`（数字声调）或 `xīng chén xīng chén`。
- 多个词用逗号 / 换行分隔，数量不限。

转换规则（`PinyinConverter`）：汉字 → 带调拼音 → 拆「声母 + 带调韵母」→ 拼成模型的关键词行：

```
x iǎo ài t óng x ué :3.0 #0.25 @小爱同学
└─ token ─────────────┘  │    │     └ 显示名
                           │    └ 触发阈值（越低越灵敏）
                           └ 提权系数（越大越容易触发这个词）
```

拼音表 `assets/kws/pinyin.tsv`（20840 字，189KB）由 `tools/gen_pinyin_table.py` 用 pypinyin 生成，
只收录「声母和韵母都能在模型 tokens.txt 里查到」的字，避免运行期拼出非法 token 静默失配。

## 3. 命中之后发生什么

1. 震动 40ms（可关）+ 通知栏变成「我在～」；
2. **应用在前台**：进程内广播 → 聊天页直接开麦（等同用户点麦克风按钮）；
3. **应用在后台**：拉起 `MainActivity` 并带 `voice_wake_trigger` extra → 界面就绪 600ms 后开麦；
   若 Android 10+ 拦截了后台启动界面，则退化成一条可点通知「点一下开始说话」；
4. 唤醒后先静默 800ms 再进 ASR，避免「星尘星尘」的尾音被当成问题内容。

## 4. 互斥与自唤醒抑制

`VoiceBus`（`voice/wake/VoiceBus.kt`）做统一门控，唤醒监听在这几种情况下**只会睡眠式跳过解码，不释放麦克风**：

- `asrActive`：`LocalAsrManager` 正在识别（它已在 startListening / stop / cancel / cleanup 里置位）；
- `ttsActive` / 播放中：通过 `AudioManager.getActivePlaybackConfigurations()` 直接问系统，
  所以 TTS、语音条、视频在响时都不会自我触发（比在每个播放器里埋钩子更可靠）；
- `wakeSettleUntil`：唤醒后的 800ms 静默期。

命中后还有 2500ms 冷却，同一句话不会连续触发。

## 5. 功耗与常驻

- 前台服务类型 `microphone`（Android 14 必须声明 `FOREGROUND_SERVICE_MICROPHONE`），常驻一条低优先级通知；
- 持 `PARTIAL_WAKE_LOCK`（12 小时上限，服务重启时续期），否则息屏后 CPU 睡下去就收不到声音；
- 采集参数 16kHz / 单声道 / 16bit，每帧 512 采样（32ms），只有 3.3M int8 模型在跑；
- 开机自启：`BootReceiver` 里读设置，`voiceWakeEnabled` 为真则拉起服务；
- **国产 ROM 需要手动放行**：设置里允许「自启动」「后台运行」「省电策略无限制」，否则息屏一段时间会被杀。
  应用内已有电池优化白名单引导（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）。

## 6. 怎么验证

### 6.1 应用内（真机最直观）

设置 → 语音设置 → 语音唤醒 → 打开 → 「录 3 秒试一下」，对着手机说唤醒词。
会显示「唤醒成功：星尘星尘（xxx ms）」或「没听到唤醒词」。

### 6.2 命令行（不依赖麦克风，验证模型与关键词）

```bash
# 1) 生成中文测试音频（Windows SAPI / Huihui 音色，输出 16k 单声道）
python tools/make_wake_test_wav.py        # -> tools/wav/{selftest,hello,negative}_16k.wav

# 2) 把 wav 放进 assets（已内置 kws/selftest.wav = 「星尘星尘」）
# 3) 触发自测（不用麦克风，直接读 assets 里的 wav）
adb shell am force-stop com.aicompanion
adb shell am start -n com.aicompanion/.ui.MainActivity --es voice_wake_test_asset kws/selftest.wav
adb logcat -s VoiceWakeTest   # 期望：hit=星尘星尘 cost=xxxms
```

### 6.3 看服务是否在跑

```bash
adb shell dumpsys activity services com.aicompanion | grep -A3 VoiceWakeService
adb logcat -s WakeWordEngine VoiceWakeService
# 正常日志：KWS 就绪 modelType=(auto) / 监听中 words=... / 唤醒命中：星尘星尘
```

## 7. 实测记录（2026-10-22）

| 场景 | 结果 |
|---|---|
| 桌面 sherpa-onnx（同模型 + 同关键词行 + Windows SAPI 合成的中文语音） | `星尘星尘` 命中、`你好星尘` 命中、4.5s 无关句不误报 = **3/3** |
| Android 模拟器（x86_64，同一份 APK）自测 wav | `KWS 就绪 modelType=(auto)`，`hit=星尘星尘`，`err=null` |
| 常驻服务 | `isForeground=true foregroundId=1201 types=00000080`（=microphone），通知通道 `voice_wake_channel`，日志 `监听中 words=星尘星尘,你好星尘 → x īng ch én …` |
| 首次初始化耗时 | 模拟器约 5~7s（int8 模型在 x86 上软算）；真机通常 0.5~1.5s，期间通知显示「正在准备…」 |
| 拼音表加载 | 20840 字 / **356~396ms**（353KB 解压 + 解析，只在服务启动时一次） |

## 8. 已知限制

1. **中文普通话为主**：模型是 wenetspeech 训练的拼音单元，方言/口音重时召回会降；英文词需要拼音化或换模型。
2. **唤醒词越长越稳**：2 个字（如「星尘」）误触发明显多于 4 个字（「星尘星尘」），推荐 4 字以上。
3. 嘈杂环境（地铁、风声）会掉召回，可把灵敏度从 0.25 调到 0.15~0.20。
4. 与 ASR 共用麦克风：识别期间唤醒暂停，识别结束自动恢复（约 40ms 内）。
5. 首帧加载模型约 100~300ms（中端机），只在服务启动时发生一次。
6. APK 体积 +约 24MB（JNI 四种 ABI + 5MB 模型）。发布正式包时可按需裁剪：

```gradle
defaultConfig {
    ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }   // 去掉 x86/x86_64，省约 10MB
}
```

## 9. 加别的语言 / 换模型

换成 sherpa-onnx 的任意 KWS 模型（中英混合等），只需：

1. 把模型目录放进 `assets/`（或换 `lib-sherpa-kws-model` 版本）；
2. 改 `WakeWordEngine.MODEL_DIR` 与三个 onnx 文件名；
3. 关键词行的 token 规则按新模型的 `tokens.txt` 调整（`PinyinConverter` 只负责中文拼音这一种）。

## 附录：主线程阻塞修复（2026-10-23）

**现象**：应用启动后点设置，弹出 ANR「isn't responding」。

**根因**：`VoiceWakeService.onStartCommand()` 里同步执行了「解析拼音表（~400ms）+ 加载 KWS int8 模型
（模拟器 5~7s）」——主线程被堵，任何触摸输入都超时。

**修复**：整段准备逻辑移到 `wake-prep` 线程；主线程只 `startForeground` 并先显示
「正在准备唤醒模型…」，就绪后切「听候「星尘星尘」」。日志里会看到
`VoiceWakeService: 服务创建` 立刻返回，随后 `WakeWordEngine: 监听中` 才出现。

**验证**：模拟器上启动 → 点设置 → 进功能引导 → 打开教程，全程无 ANR（截图见 tools/guide_*.png）。
