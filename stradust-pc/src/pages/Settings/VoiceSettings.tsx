import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Slider } from "@/components/ui/slider";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";

/**
 * 语音设置组件
 */
export function VoiceSettings() {
  const { settings, updateSettings } = useSettingsStore();
  const voice = settings.voice;

  const updateVoice = (partial: Partial<typeof voice>) => {
    updateSettings({ voice: { ...voice, ...partial } });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">语音设置</h2>

      <Card>
        <CardContent className="p-4 space-y-4">
          {/* 语音引擎 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">语音引擎</label>
            <div className="flex gap-2">
              {(["edge-tts", "vits", "gpt-sovits", "local"] as const).map((engine) => (
                <button
                  key={engine}
                  onClick={() => updateVoice({ engine })}
                  className={`px-3 py-1.5 rounded-[var(--app-radius)] text-xs transition-colors ${
                    voice.engine === engine
                      ? "bg-[var(--color-primary)] text-[var(--color-primary-foreground)]"
                      : "bg-[var(--color-muted)] text-[var(--color-muted-foreground)]"
                  }`}
                >
                  {engine}
                </button>
              ))}
            </div>
          </div>

          <Separator />

          {/* 语音ID */}
          <div className="space-y-2">
            <label className="text-sm font-medium">语音ID</label>
            <Input
              value={voice.voiceId}
              onChange={(e) => updateVoice({ voiceId: e.target.value })}
              placeholder="语音ID"
            />
          </div>

          <Separator />

          {/* 语速 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">语速</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{voice.speed.toFixed(1)}x</span>
            </div>
            <Slider
              value={[voice.speed]}
              onValueChange={([v]) => updateVoice({ speed: v })}
              min={0.5}
              max={2.0}
              step={0.1}
            />
          </div>

          {/* 音调 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">音调</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{voice.pitch.toFixed(1)}</span>
            </div>
            <Slider
              value={[voice.pitch]}
              onValueChange={([v]) => updateVoice({ pitch: v })}
              min={0.5}
              max={2.0}
              step={0.1}
            />
          </div>

          {/* 音量 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">音量</label>
              <span className="text-xs text-[var(--color-muted-foreground)]">{Math.round(voice.volume * 100)}%</span>
            </div>
            <Slider
              value={[voice.volume]}
              onValueChange={([v]) => updateVoice({ volume: v })}
              min={0}
              max={1.0}
              step={0.05}
            />
          </div>

          <Separator />

          {/* 自动播放 */}
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">自动播放语音</label>
            <Switch
              checked={voice.autoPlay}
              onCheckedChange={(checked) => updateVoice({ autoPlay: checked })}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
