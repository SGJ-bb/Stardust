/**
 * 语音设置 —— TTS（web/cloud/auto）+ 试听 + ASR + 图片生成/识别服务配置。
 * 全部读写 useSettingsStore（persist 自动落盘）；旧持久化 settings 可能缺
 * asr/imageGen/imageRecog 节，读取一律 `settings.xxx?.yyy ?? 默认值`。
 */
import { useEffect, useMemo, useState } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { Button } from "@/components/ui/button";
import { Volume2, Square, Eye, EyeOff } from "lucide-react";
import { TTS_PRESETS, ASR_PRESETS, IMAGE_GEN_PRESETS, IMAGE_RECOG_PRESETS, findPreset } from "@/engine/presets";
import type { ServicePreset } from "@/engine/presets";
import { synthesize, playAudioUrl, stopSpeaking } from "@/engine/voice";
import type { AsrConfig, ServiceEndpointConfig, VoiceEngine } from "@/types/settings";
import { Select, SectionTitle, SettingRow } from "./common";

const ENGINES: Array<{ value: string; label: string; desc: string }> = [
  { value: "web", label: "浏览器内置", desc: "使用系统自带的语音合成（speechSynthesis），离线可用、无需 Key" },
  { value: "cloud", label: "云端服务", desc: "OpenAI 兼容 TTS API（硅基流动/阿里云/Fish Audio 等），音质更好" },
  { value: "auto", label: "自动", desc: "已配置云端服务时优先云端，否则回退浏览器内置" },
];

/** 通用服务端点编辑块（TTS/ASR/图片共用形态：presetId/baseUrl/apiKey/model） */
function EndpointForm({
  presets,
  value,
  onChange,
}: {
  presets: ServicePreset[];
  /** null = 尚未配置 */
  value: ServiceEndpointConfig | null;
  onChange: (next: ServiceEndpointConfig) => void;
}) {
  const [showKey, setShowKey] = useState(false);
  const presetId = value?.presetId ?? "custom";
  const current = presets.find((p) => p.id === presetId);

  const handlePreset = (id: string) => {
    if (id === "custom") {
      onChange({ presetId: "custom", baseUrl: value?.baseUrl ?? "", apiKey: value?.apiKey ?? "", model: value?.model ?? "" });
      return;
    }
    const preset = findPreset(presets, id);
    if (!preset) return;
    onChange({
      presetId: preset.id,
      baseUrl: preset.url,
      apiKey: value?.apiKey ?? "",
      model: preset.defaultModel,
    });
  };

  return (
    <div className="space-y-3">
      <div>
        <label className="text-label block mb-1.5">服务预设</label>
        <Select
          value={presetId}
          onChange={handlePreset}
          options={[{ value: "custom", label: "自定义" }, ...presets.map((p) => ({ value: p.id, label: p.displayName }))]}
        />
      </div>
      <div>
        <label className="text-label block mb-1.5">API 地址</label>
        <Input
          value={value?.baseUrl ?? ""}
          onChange={(e) => onChange({ ...(value ?? { presetId: "custom", apiKey: "", model: "" }), baseUrl: e.target.value })}
          placeholder={current?.url ?? "https://..."}
          className="input-field font-mono text-xs"
        />
      </div>
      <div>
        <label className="text-label block mb-1.5">API Key</label>
        <div className="relative">
          <Input
            value={value?.apiKey ?? ""}
            onChange={(e) => onChange({ ...(value ?? { presetId: "custom", baseUrl: "", model: "" }), apiKey: e.target.value })}
            type={showKey ? "text" : "password"}
            placeholder="sk-..."
            className="input-field pr-10 font-mono text-xs"
          />
          <button
            type="button"
            onClick={() => setShowKey(!showKey)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)]"
          >
            {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        </div>
      </div>
      <div>
        <label className="text-label block mb-1.5">模型</label>
        <Input
          value={value?.model ?? ""}
          onChange={(e) => onChange({ ...(value ?? { presetId: "custom", baseUrl: "", apiKey: "" }), model: e.target.value })}
          placeholder={current?.defaultModel ?? "模型 ID"}
          className="input-field font-mono text-xs"
        />
      </div>
    </div>
  );
}

/**
 * 语音与视觉服务设置组件
 */
export function VoiceSettings() {
  const { settings, updateSettings } = useSettingsStore();
  const voice = settings.voice;
  // 旧引擎值（edge-tts/vits/gpt-sovits/local）在 Web 不可行，引擎侧回退 web；UI 统一按"自动"展示
  const engineChoice: "web" | "cloud" | "auto" = (["web", "cloud", "auto"] as const).includes(voice.engine as "web")
    ? (voice.engine as "web" | "cloud" | "auto")
    : "auto";
  const effectiveEngine = engineChoice;
  const isCloud = engineChoice === "cloud" || (engineChoice === "auto" && !!voice.cloud?.baseUrl);

  const updateVoice = (partial: Partial<typeof voice>) => {
    updateSettings({ voice: { ...voice, ...partial } });
  };

  // ─── web 引擎可选声音（speechSynthesis.getVoices，部分浏览器异步加载） ───
  const [webVoices, setWebVoices] = useState<SpeechSynthesisVoice[]>([]);
  useEffect(() => {
    if (typeof speechSynthesis === "undefined") return;
    const load = () => setWebVoices(speechSynthesis.getVoices());
    load();
    speechSynthesis.addEventListener("voiceschanged", load);
    return () => speechSynthesis.removeEventListener("voiceschanged", load);
  }, []);
  const zhWebVoices = useMemo(() => {
    const sorted = [...webVoices].sort((a, b) => {
      const az = a.lang.toLowerCase().startsWith("zh") ? 0 : 1;
      const bz = b.lang.toLowerCase().startsWith("zh") ? 0 : 1;
      return az - bz || a.lang.localeCompare(b.lang) || a.name.localeCompare(b.name);
    });
    return sorted;
  }, [webVoices]);

  // ─── 试听 ───
  const [auditioning, setAuditioning] = useState(false);
  const [auditionErr, setAuditionErr] = useState<string | null>(null);
  const trySpeak = async () => {
    setAuditionErr(null);
    setAuditioning(true);
    try {
      const url = await synthesize("你好，我是星尘～", {
        engine: effectiveEngine as "web" | "cloud" | "auto",
        voiceId: voice.voiceId,
        speed: voice.speed,
        pitch: voice.pitch,
        volume: voice.volume,
      });
      // web 引擎时 synthesize 内部已经直接播放（返回 null）
      if (url) playAudioUrl(url);
    } catch (e) {
      setAuditionErr((e as Error).message || "试听失败");
    } finally {
      setAuditioning(false);
    }
  };

  // ─── ASR ───
  const asr: AsrConfig = settings.asr ?? { mode: "web", language: "zh-CN" };
  const patchAsr = (partial: Partial<AsrConfig>) => updateSettings({ asr: { ...asr, ...partial } });
  const asrSupported = typeof window !== "undefined" && !!(window.SpeechRecognition || window.webkitSpeechRecognition);

  // ─── 图片生成 / 识别 ───
  const imageGen = settings.imageGen ?? null;
  const imageRecog = settings.imageRecog ?? null;

  // TTS EndpointForm 变更：切换预设时把该厂商默认音色同步进 voiceId（试听与引擎实际使用）
  const onTtsEndpointChange = (next: ServiceEndpointConfig) => {
    const preset = findPreset(TTS_PRESETS, next.presetId);
    const defaultVoice = preset?.defaultVoice;
    const cur = voice.cloud;
    const becameNewPreset = defaultVoice && (!cur || cur.presetId !== next.presetId);
    updateVoice({
      cloud: next,
      ...(becameNewPreset && (!cur?.baseUrl || !voice.voiceId || /Neural$/.test(voice.voiceId)) ? { voiceId: defaultVoice } : {}),
    });
  };

  const auditionBtn = (
    <Button size="sm" variant="outline" onClick={trySpeak} disabled={auditioning}>
      {auditioning ? (
        <>
          <Square className="h-3 w-3 mr-1" /> 合成中…
        </>
      ) : (
        <>
          <Volume2 className="h-3 w-3 mr-1" /> 试听
        </>
      )}
    </Button>
  );

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">语音与视觉服务</h2>

      {/* ═══════ TTS ═══════ */}
      <SectionTitle title="语音合成（TTS）" desc="AI 说话的声音来源与演绎参数" />
      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 引擎三选 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">TTS 引擎</label>
            <div className="grid grid-cols-3 gap-2">
              {ENGINES.map((eng) => (
                <button
                  key={eng.value}
                  onClick={() => updateVoice({ engine: eng.value as VoiceEngine })}
                  className={`flex flex-col items-start gap-1 px-3 py-2.5 rounded-lg border transition-all text-left ${
                    effectiveEngine === eng.value
                      ? "border-[var(--color-primary)] bg-[var(--color-primary)]/8"
                      : "border-[var(--color-border)] hover:border-[var(--color-muted-foreground)]"
                  }`}
                >
                  <span className={`text-xs font-semibold ${effectiveEngine === eng.value ? "text-[var(--color-primary)]" : "text-[var(--color-card-foreground)]"}`}>
                    {eng.label}
                  </span>
                  <span className="text-[10px] text-[var(--color-muted-foreground)] leading-snug">{eng.desc}</span>
                </button>
              ))}
            </div>
            {!["web", "cloud", "auto"].includes(voice.engine) && (
              <p className="text-[11px] text-[var(--color-muted-foreground)] opacity-70">
                原设置中的「{voice.engine}」引擎在 Web 端不可用，已按浏览器内置引擎执行。
              </p>
            )}
          </div>

          <Separator />

          {/* cloud：TTS 预设 + 端点表单 */}
          {isCloud && (
            <>
              <EndpointForm
                presets={TTS_PRESETS}
                value={voice.cloud ?? null}
                onChange={onTtsEndpointChange}
              />
              <div>
                <label className="text-label block mb-1.5">音色（voice）</label>
                <Input
                  value={voice.voiceId}
                  onChange={(e) => updateVoice({ voiceId: e.target.value })}
                  placeholder={findPreset(TTS_PRESETS, voice.cloud?.presetId ?? "")?.defaultVoice ?? "alloy"}
                  className="input-field font-mono text-xs"
                />
                <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                  云端音色名（如 alloy / longxiaochun）；Fish Audio 填音色 reference_id。切换上方预设会自动填入该厂商默认音色。
                </p>
              </div>
              <Separator />
            </>
          )}

          {/* web：浏览器声音选择 */}
          {effectiveEngine === "web" && (
            <>
              <div className="space-y-2">
                <label className="text-sm font-medium">浏览器声音</label>
                {zhWebVoices.length > 0 ? (
                  <select
                    value={voice.voiceId}
                    onChange={(e) => updateVoice({ voiceId: e.target.value })}
                    className="w-full rounded-[var(--app-radius)] border border-[var(--color-border)] bg-[var(--color-card)] px-3 py-2 text-sm"
                  >
                    {!zhWebVoices.some((v) => v.name === voice.voiceId) && (
                      <option value={voice.voiceId}>{voice.voiceId}（当前近似匹配）</option>
                    )}
                    {zhWebVoices.map((v) => (
                      <option key={`${v.lang}-${v.name}`} value={v.name}>
                        {v.name}（{v.lang}）{v.default ? " · 系统默认" : ""}
                      </option>
                    ))}
                  </select>
                ) : (
                  <p className="text-xs text-[var(--color-muted-foreground)]">
                    未检测到可用的浏览器声音。仍会按语音 ID「{voice.voiceId}」的语言前缀近似选择系统声音。
                  </p>
                )}
                <p className="text-[10px] text-[var(--color-muted-foreground)] opacity-60">
                  列表来自操作系统已安装 TTS 语音包，中文声音排在前面。
                </p>
              </div>
              <Separator />
            </>
          )}

          {/* 语音ID（auto 且未配置云端时的兜底展示：影响 web 引擎近似选声） */}
          {!isCloud && !["web"].includes(engineChoice) && (
            <div className="space-y-2">
              <label className="text-sm font-medium">语音 ID（音色倾向）</label>
              <Input
                value={voice.voiceId}
                onChange={(e) => updateVoice({ voiceId: e.target.value })}
                placeholder="如 zh-CN-XiaoxiaoNeural"
              />
              <p className="text-[10px] text-[var(--color-muted-foreground)] opacity-60">
                自动模式下暂未配置云端服务：将按此 ID 的语言近似选择浏览器声音。
              </p>
            </div>
          )}

          {/* 语速/音调/音量 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">语速</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{voice.speed.toFixed(1)}x</span>
            </div>
            <Slider value={[voice.speed]} onValueChange={([v]) => updateVoice({ speed: v })} min={0.5} max={2.0} step={0.1} />
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">音调</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{voice.pitch.toFixed(1)}</span>
            </div>
            <Slider value={[voice.pitch]} onValueChange={([v]) => updateVoice({ pitch: v })} min={0.5} max={2.0} step={0.1} />
          </div>
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">音量</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{Math.round(voice.volume * 100)}%</span>
            </div>
            <Slider value={[voice.volume]} onValueChange={([v]) => updateVoice({ volume: v })} min={0} max={1.0} step={0.05} />
          </div>

          <Separator />

          {/* 自动播放 + 试听 */}
          <SettingRow title="自动播放语音" desc="AI 回复后直接朗读（关闭则仅在点击气泡播放）">
            {auditionBtn}
            <Switch checked={voice.autoPlay} onCheckedChange={(checked) => updateVoice({ autoPlay: checked })} />
          </SettingRow>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="ghost" onClick={() => stopSpeaking()}>
              <Square className="h-3.5 w-3.5 mr-1" /> 停止播放
            </Button>
            {auditionErr && <span className="text-xs text-red-400">{auditionErr}</span>}
            <span className="text-[10px] text-[var(--color-muted-foreground)] opacity-60">试听内容：「你好，我是星尘～」</span>
          </div>
        </CardContent>
      </Card>

      {/* ═══════ ASR ═══════ */}
      <SectionTitle title="语音识别（ASR）" desc="把麦克风里的话转成文字发给 AI" />
      <Card>
        <CardContent className="p-4 space-y-4">
          <div className="space-y-2">
            <label className="text-sm font-medium">识别引擎</label>
            <div className="flex gap-2">
              {([
                { value: "web", label: "浏览器内置" },
                { value: "cloud", label: "云端 Whisper" },
              ] as const).map((m) => (
                <button
                  key={m.value}
                  onClick={() => patchAsr({ mode: m.value })}
                  className={`px-3 py-1.5 rounded-[var(--app-radius)] text-xs transition-colors ${
                    asr.mode === m.value
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)]"
                  }`}
                >
                  {m.label}
                </button>
              ))}
            </div>
            {asr.mode === "web" && !asrSupported && (
              <p className="text-[11px] text-yellow-500">当前浏览器不支持 SpeechRecognition，建议改用云端识别。</p>
            )}
          </div>

          <div className="grid grid-cols-2 items-center gap-3">
            <label className="text-sm font-medium">识别语言</label>
            <Select
              value={asr.language ?? "zh-CN"}
              onChange={(v) => patchAsr({ language: v })}
              options={[
                { value: "zh-CN", label: "简体中文" },
                { value: "zh-TW", label: "繁体中文" },
                { value: "en-US", label: "英语" },
                { value: "ja-JP", label: "日语" },
                { value: "ko-KR", label: "韩语" },
              ]}
            />
          </div>

          {asr.mode === "cloud" && (
            <>
              <Separator />
              <EndpointForm
                presets={ASR_PRESETS}
                value={asr.cloud ?? null}
                onChange={(next) => patchAsr({ cloud: next })}
              />
              <p className="text-[10px] text-[var(--color-muted-foreground)] opacity-60">
                云端识别走 OpenAI Whisper 兼容接口（multipart 上传录音）。
              </p>
            </>
          )}
        </CardContent>
      </Card>

      {/* ═══════ 图片生成 ═══════ */}
      <SectionTitle title="图片生成服务" desc="AI 工具 generate_image 画图的模型服务；不配置则 AI 无法生图" />
      <Card>
        <CardContent className="p-4">
          <EndpointForm
            presets={IMAGE_GEN_PRESETS}
            value={imageGen}
            onChange={(next) => updateSettings({ imageGen: next })}
          />
        </CardContent>
      </Card>

      {/* ═══════ 图片识别 ═══════ */}
      <SectionTitle title="图片识别服务" desc="让 AI「看图说话」的多模态视觉模型；未配置时回退主聊天模型（需其支持视觉）" />
      <Card>
        <CardContent className="p-4">
          <EndpointForm
            presets={IMAGE_RECOG_PRESETS}
            value={imageRecog}
            onChange={(next) => updateSettings({ imageRecog: next })}
          />
        </CardContent>
      </Card>
    </div>
  );
}
