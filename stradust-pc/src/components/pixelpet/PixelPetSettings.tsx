import { useState, useCallback } from "react";
import { usePixelPetStore } from "@/stores/usePixelPetStore";
import { Settings, Save, TestTube, AlertTriangle, CheckCircle } from "lucide-react";
import type { ImageGenConfig, ImageGenProvider } from "@/lib/pixelpet/types";
import {
  DEFAULT_GEN_CONFIG,
} from "@/lib/pixelpet/types";

interface PixelPetSettingsProps {
  /** 设置保存回调 */
  onSaved?: () => void;
}

/**
 * 像素宠物设置面板
 *
 * 包含：
 * - 图片生成API配置（提供商/URL/Key/模型）
 * - 像素风格参数（尺寸/风格提示词/步数/CFG）
 * - 测试连接功能
 */
export function PixelPetSettings({ onSaved }: PixelPetSettingsProps) {
  const { genConfig, updateGenConfig, testGenApi } = usePixelPetStore();
  const [provider, setProvider] = useState<ImageGenProvider>(genConfig.provider);
  const [apiUrl, setApiUrl] = useState(genConfig.apiUrl);
  const [apiKey, setApiKey] = useState(genConfig.apiKey);
  const [model, setModel] = useState(genConfig.model);
  const [stylePrompt, setStylePrompt] = useState(genConfig.stylePrompt);
  const [size, setSize] = useState(genConfig.size);
  const [steps, setSteps] = useState(genConfig.steps);
  const [cfgScale, setCfgScale] = useState(genConfig.cfgScale);
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);

  // ═══ 保存配置 ═══
  const handleSave = useCallback(async () => {
    await updateGenConfig({
      provider,
      apiUrl,
      apiKey,
      model,
      stylePrompt,
      size,
      steps,
      cfgScale,
    });
    onSaved?.();
  }, [provider, apiUrl, apiKey, model, stylePrompt, size, steps, cfgScale, updateGenConfig, onSaved]);

  // ═══ 测试API连接 ═══
  const handleTest = useCallback(async () => {
    if (!apiUrl || !apiKey) {
      setTestResult({ ok: false, msg: '请先填写 API 地址和密钥' });
      return;
    }

    setIsTesting(true);
    setTestResult(null);

    try {
      // 先保存当前配置以便测试
      await updateGenConfig({ provider, apiUrl, apiKey, model, stylePrompt, size, steps, cfgScale });

      // 调用测试命令
      const { invoke } = await import('@tauri-apps/api/core');
      // 使用一个最小提示词测试
      const result = await invoke<string>('test_pixel_gen_api', {
        prompt: 'a simple pixel art dot, 64x64 pixels',
      });
      setTestResult({ ok: true, msg: '连接成功！API响应正常' });
    } catch (e) {
      setTestResult({ ok: false, msg: e instanceof Error ? e.message : String(e) });
    } finally {
      setIsTesting(false);
    }
  }, [provider, apiUrl, apiKey, model, stylePrompt, size, steps, cfgScale, updateGenConfig]);

  // ═══ 恢复默认像素风格 ═══
  const handleResetStyle = useCallback(() => {
    setStylePrompt(DEFAULT_GEN_CONFIG.stylePrompt);
    setSize(DEFAULT_GEN_CONFIG.size);
    setSteps(DEFAULT_GEN_CONFIG.steps);
    setCfgScale(DEFAULT_GEN_CONFIG.cfgScale);
  }, []);

  const providerOptions: { value: ImageGenProvider; label: string; placeholderUrl: string }[] = [
    { value: 'openai', label: 'OpenAI DALL-E', placeholderUrl: 'https://api.openai.com/v1/images/generations' },
    { value: 'stability', label: 'Stability AI', placeholderUrl: 'https://api.stability.ai/v2beta/stable-image/generate/sd3' },
    { value: 'comfyui', label: 'ComfyUI (本地)', placeholderUrl: 'http://127.0.0.1:8188' },
    { value: 'custom', label: '通用SD WebUI', placeholderUrl: 'http://127.0.0.1:7860' },
    { value: 'local_sd', label: '本地 SD API', placeholderUrl: 'http://127.0.0.1:7860' },
  ];

  const currentProviderOpt = providerOptions.find((p) => p.value === provider);

  return (
    <div className="flex flex-col gap-4 max-w-lg">
      <div className="flex items-center gap-2">
        <Settings className="h-4 w-4 text-primary" />
        <span className="font-medium text-sm">图片生成 API 配置</span>
      </div>

      {/* API 提供商 */}
      <div>
        <label className="text-xs text-gray-400 mb-1 block">API 提供商</label>
        <select
          value={provider}
          onChange={(e) => {
            setProvider(e.target.value as ImageGenProvider);
            const opt = providerOptions.find((p) => p.value === e.target.value);
            if (opt && !apiUrl) setApiUrl(opt.placeholderUrl);
          }}
          className="w-full bg-black/20 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:border-primary outline-none"
        >
          {providerOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {/* API URL */}
      <div>
        <label className="text-xs text-gray-400 mb-1 block">API 地址</label>
        <input
          value={apiUrl}
          onChange={(e) => setApiUrl(e.target.value)}
          placeholder={currentProviderOpt?.placeholderUrl || 'https://...'}
          className="w-full bg-black/20 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none"
        />
      </div>

      {/* API Key */}
      <div>
        <label className="text-xs text-gray-400 mb-1 block">API 密钥</label>
        <input
          type="password"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
          placeholder="sk-..."
          className="w-full bg-black/20 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none font-mono"
        />
      </div>

      {/* 模型名称 */}
      {(provider === 'openai') && (
        <div>
          <label className="text-xs text-gray-400 mb-1 block">模型名称</label>
          <input
            value={model}
            onChange={(e) => setModel(e.target.value)}
            placeholder="dall-e-3"
            className="w-full bg-black/20 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none"
          />
        </div>
      )}

      {/* 分隔线：像素参数 */}
      <div className="border-t border-white/5 pt-3">
        <div className="text-xs text-gray-500 mb-2">像素风格参数</div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-[10px] text-gray-500 mb-1 block">输出尺寸</label>
            <select
              value={size}
              onChange={(e) => setSize(e.target.value)}
              className="w-full bg-black/20 border border-white/10 rounded-lg px-2 py-1.5 text-xs text-white focus:border-primary outline-none"
            >
              <option value="32x32">32x32</option>
              <option value="48x48">48x48</option>
              <option value="64x64">64x64</option>
              <option value="96x96">96x96</option>
              <option value="128x128">128x128</option>
            </select>
          </div>
          <div>
            <label className="text-[10px] text-gray-500 mb-1 block">生成步数</label>
            <input
              type="number"
              value={steps}
              onChange={(e) => setSteps(Number(e.target.value))}
              min={5}
              max={50}
              className="w-full bg-black/20 border border-white/10 rounded-lg px-2 py-1.5 text-xs text-white focus:border-primary outline-none"
            />
          </div>
          <div>
            <label className="text-[10px] text-gray-500 mb-1 block">CFG Scale</label>
            <input
              type="number"
              value={cfgScale}
              onChange={(e) => setCfgScale(Number(e.target.value))}
              min={1}
              max={20}
              step={0.5}
              className="w-full bg-black/20 border border-white/10 rounded-lg px-2 py-1.5 text-xs text-white focus:border-primary outline-none"
            />
          </div>
        </div>

        {/* 风格提示词 */}
        <div className="mt-2">
          <div className="flex items-center justify-between mb-1">
            <label className="text-[10px] text-gray-500">像素风格修饰符</label>
            <button onClick={handleResetStyle} className="text-[10px] text-gray-500 hover:text-gray-300 transition-colors">
              恢复默认
            </button>
          </div>
          <textarea
            value={stylePrompt}
            onChange={(e) => setStylePrompt(e.target.value)}
            rows={2}
            className="w-full bg-black/20 border border-white/10 rounded-lg px-2.5 py-1.5 text-[11px] text-gray-300 placeholder:text-gray-600 focus:border-primary outline-none resize-none"
          />
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex gap-2 pt-1">
        <button
          onClick={handleTest}
          disabled={isTesting || !apiUrl || !apiKey}
          className="flex-1 py-2 text-xs bg-white/5 hover:bg-white/10 disabled:opacity-30 text-white rounded-lg transition-colors flex items-center justify-center gap-1.5"
        >
          <TestTube className="h-3.5 w-3.5" />
          {isTesting ? '测试中...' : '测试连接'}
        </button>
        <button
          onClick={handleSave}
          className="flex-1 py-2 text-xs bg-primary hover:bg-primary/80 text-white rounded-lg transition-colors flex items-center justify-center gap-1.5"
        >
          <Save className="h-3.5 w-3.5" /> 保存配置
        </button>
      </div>

      {/* 测试结果 */}
      {testResult && (
        <div className={`rounded-lg p-2 text-xs flex items-start gap-2 ${
          testResult.ok ? 'bg-green-500/10 text-green-400' : 'bg-red-500/10 text-red-400'
        }`}>
          {testResult.ok ? (
            <CheckCircle className="h-4 w-4 flex-shrink-0 mt-0.5" />
          ) : (
            <AlertTriangle className="h-4 w-4 flex-shrink-0 mt-0.5" />
          )}
          <span>{testResult.msg}</span>
        </div>
      )}
    </div>
  );
}
