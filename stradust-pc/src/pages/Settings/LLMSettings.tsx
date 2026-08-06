import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Plus, Trash2, Check, Eye, EyeOff, Sparkles, Zap, Globe, Server, Brain, Cpu, Cloud, Search, Play, Moon, Maximize2, Hexagon } from "lucide-react";
import { generateId } from "@/lib/utils";
import type { ProviderProfile, LlmProvider } from "@/types/settings";
import { useState, useCallback } from "react";

/** 内置厂商模板 — 用户无需自己找 URL */
const BUILTIN_PROVIDERS: Array<{
  key: LlmProvider;
  name: string;
  description: string;
  icon: typeof Sparkles;
  baseUrl: string;
  baseUrlPlaceholder: string;
  defaultModels: string[];
  color: string;
  apiKeyHint: string;   // API Key 获取提示
  free?: boolean;       // 是否免费
}> = [
  // ═══════════ 海外厂商 ═══════════
  {
    key: "openai",
    name: "OpenAI",
    description: "GPT-4o / GPT-4o-mini / o1 / o3",
    icon: Sparkles,
    baseUrl: "https://api.openai.com/v1",
    baseUrlPlaceholder: "https://api.openai.com/v1",
    defaultModels: ["gpt-4o", "gpt-4o-mini", "o1", "o3", "gpt-4-turbo"],
    color: "#10a37f",
    apiKeyHint: "在 platform.openai.com/api-keys 获取",
  },
  {
    key: "anthropic",
    name: "Anthropic Claude",
    description: "Claude 4 Opus / Sonnet / Haiku",
    icon: Zap,
    baseUrl: "https://api.anthropic.com",
    baseUrlPlaceholder: "https://api.anthropic.com",
    defaultModels: ["claude-opus-4-20250514", "claude-sonnet-4-20250514", "claude-haiku-4-20250514"],
    color: "#d97706",
    apiKeyHint: "在 console.anthropic.com/settings/keys 获取",
  },
  {
    key: "google",
    name: "Google Gemini",
    description: "Gemini 2.5 Pro / Flash / Flash-Lite",
    icon: Globe,
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai",
    baseUrlPlaceholder: "https://generativelanguage.googleapis.com/v1beta/openai",
    defaultModels: ["gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"],
    color: "#4285f4",
    apiKeyHint: "在 aistudio.google.com/apikey 获取",
  },

  // ═══════════ 国内厂商（按热度排序） ═══════════
  {
    key: "deepseek",
    name: "DeepSeek (深度求索)",
    description: "DeepSeek V3 / R1 推理模型 / Coder",
    icon: Brain,
    baseUrl: "https://api.deepseek.com/v1",
    baseUrlPlaceholder: "https://api.deepseek.com/v1",
    defaultModels: ["deepseek-chat", "deepseek-reasoner", "deepseek-coder"],
    color: "#4d6bfe",
    apiKeyHint: "在 platform.deepseek.com/api_keys 获取，注册即送免费额度",
  },
  {
    key: "moonshot",
    name: "Moonshot (Kimi 智能助手)",
    description: "Kimi K2 / Moonshot v1 系列",
    icon: Moon,
    baseUrl: "https://api.moonshot.cn/v1",
    baseUrlPlaceholder: "https://api.moonshot.cn/v1",
    defaultModels: ["kimi-latest", "moonshot-v1-128k", "moonshot-v1-32k", "moonshot-v1-8k"],
    color: "#7c3aed",
    apiKeyHint: "在 platform.moonshot.cn/console/api-keys 获取",
  },
  {
    key: "zhipu",
    name: "智谱 AI (GLM)",
    description: "GLM-4-Plus / Flash / Air / CodeGeeX",
    icon: Cpu,
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    baseUrlPlaceholder: "https://open.bigmodel.cn/api/paas/v4",
    defaultModels: ["glm-4-plus", "glm-4-flash", "glm-4-air", "codegeex-4"],
    color: "#0ea5e9",
    apiKeyHint: "在 open.bigmodel.cn/usercenter/apikeys 获取",
  },
  {
    key: "tongyi",
    name: "阿里 通义千问 (Qwen)",
    description: "Qwen-Max / Plus / Turbo / VL 视觉",
    icon: Cloud,
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    baseUrlPlaceholder: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    defaultModels: ["qwen-max", "qwen-plus", "qwen-turbo", "qwen-vl-max", "qwen-long"],
    color: "#ff6a00",
    apiKeyHint: "在 dashscope.console.aliyun.com/apiKey 获取",
  },
  {
    key: "doubao",
    name: "字节 豆包 (Doubao)",
    description: "Doubao-Pro / Lite / 大模型",
    icon: Play,
    baseUrl: "https://ark.cn-beijing.volces.com/api/v3",
    baseUrlPlaceholder: "https://ark.cn-beijing.volces.com/api/v3",
    defaultModels: ["doubao-pro-32k", "doubao-lite-32k", "doubao-pro-128k", "doubao-1.5-pro-32k"],
    color: "#0066ff",
    apiKeyHint: "在 console.volcengine.com/ark/center 获取 API Key + Endpoint ID（填入 URL）",
  },
  {
    key: "baidu",
    name: "百度 文心一言 (ERNIE)",
    description: "ERNIE 4.0 / 3.5 / Speed / Lite",
    icon: Search,
    baseUrl: "https://qianfan.baidubce.com/v2",
    baseUrlPlaceholder: "https://qianfan.baidubce.com/v2",
    defaultModels: ["ernie-4.0-turbo-8k", "ernie-3.5-8k", "ernie-speed-8k", "ernie-lite-8k"],
    color: "#2932e1",
    apiKeyHint: "在 console.bce.baidu.com/qianfan/ais/console/applicationConsole/application 获取 API Key + Secret Key",
  },
  {
    key: "yiwanyiwu",
    name: "零一万物 (Yi)",
    description: "Yi-Large / Medium / Spark / Vision",
    icon: Hexagon,
    baseUrl: "https://api.lingyiwanwu.com/v1",
    baseUrlPlaceholder: "https://api.lingyiwanwu.com/v1",
    defaultModels: ["yi-large", "yi-medium", "yi-spark", "yi-vision"],
    color: "#e8523d",
    apiKeyHint: "在 platform.lingyiwanwu.com/apikeys 获取",
  },
  {
    key: "minimax",
    name: "MiniMax (海螺 AI)",
    description: "abab 6.5s / 6.5t / abab 6",
    icon: Maximize2,
    baseUrl: "https://api.minimax.chat/v1",
    baseUrlPlaceholder: "https://api.minimax.chat/v1",
    defaultModels: ["abab6.5s-chat", "abab6.5t-chat", "abab6-chat"],
    color: "#f97316",
    apiKeyHint: "在 platform.minimaxi.com/document/Account%20Guide 获取 API Key + Group ID",
  },

  // ═══════════ 本地部署 ═══════════
  {
    key: "local",
    name: "Ollama (本地)",
    description: "本地运行开源模型，免费无限用",
    icon: Server,
    baseUrl: "http://localhost:11434/v1",
    baseUrlPlaceholder: "http://localhost:11434/v1",
    defaultModels: ["qwen3:32b", "deepseek-r1:14b", "llama3.3:70b", "phi4:14b", "gemma3:27b"],
    color: "#6366f1",
    apiKeyHint: "",
    free: true,
  },
];

/**
 * LLM 设置组件 — 用户友好的内置厂商模板
 */
export function LLMSettings() {
  const { settings, addProvider, deleteProvider, setActiveProvider, updateProvider } = useSettingsStore();
  const [showAddForm, setShowAddForm] = useState(false);
  const [showApiKey, setShowApiKey] = useState<string | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<LlmProvider>("openai");
  const [newProvider, setNewProvider] = useState({
    name: "",
    provider: "openai" as LlmProvider,
    apiKey: "",
    baseUrl: "",
    modelId: "",
  });

  // 测试状态
  const [testingProviderId, setTestingProviderId] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<Record<string, { ok: boolean; msg: string; latency?: number }>>({});

  /** 选择厂商模板时自动填充 */
  const handleSelectTemplate = useCallback((key: LlmProvider) => {
    const template = BUILTIN_PROVIDERS.find((t) => t.key === key);
    if (!template) return;

    setSelectedTemplate(key);
    setNewProvider((prev) => ({
      ...prev,
      provider: key,
      // 名称自动填充，用户可改
      name: prev.name || template.name,
      // Base URL 自动填充
      baseUrl: template.baseUrl,
      // 模型 ID 自动填入第一个推荐模型
      modelId: prev.modelId || template.defaultModels[0],
    }));
  }, []);

  const handleAdd = () => {
    if (!newProvider.name.trim()) return;
    // local 模式不需要 API Key
    if (newProvider.provider !== "local" && !newProvider.apiKey.trim()) return;

    const provider: ProviderProfile = {
      id: generateId(),
      ...newProvider,
      params: {
        temperature: 0.7,
        topP: 0.9,
        maxTokens: 2048,
        presencePenalty: 0,
        frequencyPenalty: 0,
        stopSequences: [],
      },
      isDefault: settings.providers.length === 0,
    };
    addProvider(provider);
    if (settings.providers.length === 0) {
      setActiveProvider(provider.id);
    }
    setShowAddForm(false);
    setNewProvider({ name: "", provider: "openai", apiKey: "", baseUrl: "", modelId: "" });
    setSelectedTemplate("openai");
  };

  /** 测试 LLM 连接 */
  const testConnection = async (provider: ProviderProfile) => {
    setTestingProviderId(provider.id);
    setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: "测试中..." } }));

    const startTime = Date.now();

    try {
      if (provider.provider === "local") {
        // Ollama 本地模型检测 — 去除可能的 /v1 后缀
        let ollamaUrl = provider.baseUrl.replace(/\/+$/, "");
        if (ollamaUrl.endsWith("/v1")) ollamaUrl = ollamaUrl.slice(0, -3);
        const res = await fetch(`${ollamaUrl}/api/tags`, {
          method: "GET",
          signal: AbortSignal.timeout(8000),
        });
        const data = await res.json();
        const models = data?.models ?? [];
        const latency = Date.now() - startTime;
        if (res.ok && Array.isArray(models)) {
          setTestResult(prev => ({ ...prev, [provider.id]: {
            ok: true, msg: `连接成功！已安装 ${models.length} 个模型`, latency
          }}));
        } else {
          setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: "Ollama 未运行或地址错误" } }));
        }
      } else {
        // OpenAI 兼容 API 测试 — 发送一个最小请求
        const res = await fetch(`${provider.baseUrl}/chat/completions`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${provider.apiKey}`,
          },
          body: JSON.stringify({
            model: provider.modelId,
            messages: [{ role: "user", content: "hi" }],
            max_tokens: 5,
            stream: false,
          }),
          signal: AbortSignal.timeout(15000),
        });
        const latency = Date.now() - startTime;

        if (res.ok) {
          const data = await res.json();
          const modelInfo = data?.model || provider.modelId;
          setTestResult(prev => ({ ...prev, [provider.id]: {
            ok: true, msg: `连接成功！模型: ${modelInfo}`, latency
          }}));
        } else {
          let errMsg = `HTTP ${res.status}`;
          try {
            const errData = await res.json();
            errMsg = errData?.error?.message || errData?.error || errMsg;
          } catch {}
          setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: errMsg } }));
        }
      }
    } catch (err: any) {
      const errMsg = err.name === "TimeoutError" ? "连接超时" : (err.message || "网络错误");
      setTestResult(prev => ({ ...prev, [provider.id]: { ok: false, msg: errMsg } }));
    } finally {
      setTestingProviderId(null);
    }
  };

  /** 获取当前选中模板的推荐模型列表 */
  const currentTemplate = BUILTIN_PROVIDERS.find((t) => t.key === selectedTemplate);

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">AI 模型配置</h2>
          <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
            选择内置厂商或自定义 API 接口
          </p>
        </div>
        <Button onClick={() => setShowAddForm(true)} size="sm" className="btn-primary">
          <Plus className="h-4 w-4 mr-1" />
          添加模型
        </Button>
      </div>

      {/* ====== 已配置的提供商列表 ====== */}
      {settings.providers.length > 0 && (
        <div className="space-y-3">
          {settings.providers.map((provider) => {
            const template = BUILTIN_PROVIDERS.find((t) => t.key === provider.provider);
            return (
              <Card key={provider.id} className="surface-card">
                <CardContent className="p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      {/* 厂商图标/色块 */}
                      <div
                        className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
                        style={{ background: `${template?.color ?? "#666"}15` }}
                      >
                        {template ? (
                          <template.icon className="h-5 w-5" style={{ color: template.color }} />
                        ) : (
                          <Server className="h-5 w-5 text-[var(--color-muted-foreground)]" />
                        )}
                      </div>

                      {/* 信息区 */}
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <h3 className="font-medium text-sm text-[var(--color-card-foreground)] truncate">
                            {provider.name}
                          </h3>
                          {settings.activeProviderId === provider.id && (
                            <span className="shrink-0 text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-[var(--color-primary)]/10 text-[var(--color-primary)] flex items-center gap-0.5">
                              <Check className="h-2.5 w-2.5" /> 当前使用
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5 truncate">
                          模型: <span className="font-mono">{provider.modelId}</span>
                        </p>
                        {provider.baseUrl && (
                          <p className="text-[11px] text-[var(--color-muted-foreground)] mt-0.5 truncate opacity-60 font-mono">
                            {provider.baseUrl}
                          </p>
                        )}
                      </div>
                    </div>

                    {/* 操作按钮 */}
                    <div className="flex gap-1.5 shrink-0 ml-3">
                      {/* 测试连接按钮 */}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => testConnection(provider)}
                        disabled={testingProviderId === provider.id}
                        className="text-xs"
                      >
                        {testingProviderId === provider.id ? (
                          <span className="flex items-center gap-1">
                            <span className="h-3 w-3 border border-current border-t-transparent rounded-full animate-spin" />
                            测试中
                          </span>
                        ) : (
                          <>
                            <Play className="h-3 w-3 mr-0.5" />
                            测试
                          </>
                        )}
                      </Button>
                      {settings.activeProviderId !== provider.id && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setActiveProvider(provider.id)}
                          className="text-xs"
                        >
                          使用
                        </Button>
                      )}
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => deleteProvider(provider.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5 text-[var(--color-muted-foreground)]" />
                      </Button>
                    </div>
                  </div>
                  {/* 测试结果 */}
                  {testResult[provider.id] && (
                    <div className={`mt-2 text-xs px-2 py-1.5 rounded-md flex items-center gap-1.5 ${
                      testResult[provider.id].ok
                        ? "bg-green-500/10 text-green-400"
                        : "bg-red-500/10 text-red-400"
                    }`}>
                      {testResult[provider.id].ok ? (
                        <Check className="h-3 w-3 shrink-0" />
                      ) : (
                        <span className="h-3 w-3 shrink-0 rounded-full bg-red-400" />
                      )}
                      <span>{testResult[provider.id].msg}</span>
                      {testResult[provider.id].latency && (
                        <span className="ml-auto opacity-60">{testResult[provider.id].latency}ms</span>
                      )}
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {settings.providers.length === 0 && (
        <div className="surface-card p-8 text-center">
          <Server className="h-10 w-10 mx-auto mb-3 text-[var(--color-muted-foreground)] opacity-40" />
          <p className="text-sm text-[var(--color-muted-foreground)]">还没有配置 AI 模型</p>
          <p className="text-xs text-[var(--color-muted-foreground)] mt-1 opacity-60">
            选择下方内置厂商快速添加，或点击「添加模型」自定义
          </p>
        </div>
      )}

      {/* ====== 快速添加：内置厂商选择 ====== */}
      {!showAddForm && settings.providers.length === 0 && (
        <div>
          <p className="sidebar-section-label mb-3">快速添加（内置厂商）</p>
          <div className="grid grid-cols-3 gap-2.5">
            {BUILTIN_PROVIDERS.map((template) => {
              const Icon = template.icon;
              return (
                <button
                  key={template.key}
                  onClick={() => {
                    handleSelectTemplate(template.key);
                    setNewProvider({
                      name: template.name,
                      provider: template.key,
                      apiKey: "",
                      baseUrl: template.baseUrl,
                      modelId: template.defaultModels[0],
                    });
                    setShowAddForm(true);
                  }}
                  className="surface-card p-4 text-left group transition-all duration-200 hover:-translate-y-0.5"
                >
                  <div className="flex items-center gap-2.5 mb-2">
                    <div
                      className="w-8 h-8 rounded-lg flex items-center justify-center"
                      style={{ background: `${template.color}12` }}
                    >
                      <Icon className="h-4 w-4" style={{ color: template.color }} />
                    </div>
                    <span className="font-semibold text-sm text-[var(--color-card-foreground)]">
                      {template.name}
                    </span>
                  </div>
                  <p className="text-[11px] text-[var(--color-muted-foreground)] leading-relaxed">
                    {template.description}
                  </p>
                  {(template.key === "local" || template.key === "deepseek") && (
                    <span className="inline-block mt-1.5 text-[10px] font-medium px-1.5 py-0.5 rounded-full bg-green-500/10 text-green-500">
                      {template.key === "local" ? "免费" : "送额度"}
                    </span>
                  )}
                </button>
              );
            })}
          </div>

          {/* 自定义入口 */}
          <button
            onClick={() => {
              setSelectedTemplate("openai");
              setNewProvider({ name: "", provider: "openai", apiKey: "", baseUrl: "", modelId: "" });
              setShowAddForm(true);
            }}
            className="w-full surface-card p-3 text-center text-sm text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)] transition-colors mt-3"
          >
            + 自定义 API 接口（OpenAI 兼容格式）
          </button>
        </div>
      )}

      {/* ====== 添加/编辑表单 ====== */}
      {showAddForm && (
        <Card className="surface-card">
          <CardContent className="p-5 space-y-4">
            <h3 className="font-medium text-sm text-[var(--color-card-foreground)]">
              {settings.providers.length === 0 ? "添加 AI 模型" : "添加新模型"}
            </h3>

            {/* 厂商选择 */}
            <div>
              <label className="text-label block mb-2">选择厂商</label>
              <div className="grid grid-cols-3 gap-2">
                {BUILTIN_PROVIDERS.map((template) => {
                  const Icon = template.icon;
                  const isActive = selectedTemplate === template.key;
                  return (
                    <button
                      key={template.key}
                      onClick={() => handleSelectTemplate(template.key)}
                      className={`relative flex items-center gap-2 px-3 py-2.5 rounded-lg border transition-all duration-200 ${
                        isActive
                          ? "border-[var(--color-primary)] bg-[var(--color-primary)]/8"
                          : "border-[var(--color-border)] hover:border-[var(--color-muted-foreground)]"
                      }`}
                    >
                      <Icon
                        className="h-4 w-4 shrink-0"
                        style={{ color: isActive ? "var(--color-primary)" : template.color }}
                      />
                      <span className={`text-xs font-medium ${isActive ? "text-[var(--color-primary)]" : ""}`}>
                        {template.name}
                      </span>
                      {isActive && (
                        <div
                          className="absolute bottom-0 left-1/2 -translate-x-1/2 w-6 h-0.5 rounded-full"
                          style={{ background: "var(--color-primary)" }}
                        />
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* 名称 */}
            <div>
              <label className="text-label block mb-1.5">显示名称</label>
              <Input
                value={newProvider.name}
                onChange={(e) => setNewProvider({ ...newProvider, name: e.target.value })}
                placeholder="例如：我的 GPT-4o"
                className="input-field"
              />
            </div>

            {/* API Key（local 模式不需要） */}
            {selectedTemplate !== "local" && (
              <div>
                <label className="text-label block mb-1.5">API Key</label>
                <div className="relative">
                  <Input
                    value={newProvider.apiKey}
                    onChange={(e) => setNewProvider({ ...newProvider, apiKey: e.target.value })}
                    placeholder={selectedTemplate === "openai" ? "sk-..." : selectedTemplate === "anthropic" ? "sk-ant-..." : "输入 API Key"}
                    type={showApiKey === "main" ? "text" : "password"}
                    className="input-field pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey(showApiKey === "main" ? null : "main")}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-muted-foreground)] hover:text-[var(--color-card-foreground)]"
                  >
                    {showApiKey === "main" ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                  {currentTemplate?.apiKeyHint || "在对应厂商开放平台获取 API Key"}
                </p>
              </div>
            )}

            {/* Base URL */}
            <div>
              <label className="text-label block mb-1.5">API 地址</label>
              <Input
                value={newProvider.baseUrl}
                onChange={(e) => setNewProvider({ ...newProvider, baseUrl: e.target.value })}
                placeholder={currentTemplate?.baseUrlPlaceholder ?? "https://api.example.com/v1"}
                className="input-field font-mono text-xs"
              />
              {selectedTemplate === "local" && (
                <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">
                  确保已安装并启动 Ollama：ollama serve
                </p>
              )}
            </div>

            {/* 模型 ID */}
            <div>
              <label className="text-label block mb-1.5">模型</label>
              <Input
                value={newProvider.modelId}
                onChange={(e) => setNewProvider({ ...newProvider, modelId: e.target.value })}
                placeholder="输入模型 ID"
                className="input-field font-mono text-xs"
              />
              {/* 推荐模型快捷选择 */}
              {currentTemplate && (
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {currentTemplate.defaultModels.map((model) => (
                    <button
                      key={model}
                      onClick={() => setNewProvider({ ...newProvider, modelId: model })}
                      className={`chip text-[11px] ${newProvider.modelId === model ? "active" : ""}`}
                    >
                      {model}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* 按钮 */}
            <div className="flex gap-2 pt-1">
              <Button
                onClick={handleAdd}
                disabled={!newProvider.name.trim() || (selectedTemplate !== "local" && !newProvider.apiKey.trim())}
                className="btn-primary"
              >
                确认添加
              </Button>
              <Button variant="outline" onClick={() => setShowAddForm(false)}>
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
