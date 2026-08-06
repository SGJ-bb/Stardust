import { useParams, useNavigate } from "react-router";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ArrowLeft, Save, Sparkles, Loader2 } from "lucide-react";
import { useState, useEffect } from "react";
import type { PersonaGender } from "@/types/persona";

/**
 * 角色编辑页面 — 含 AI 辅助生成人格设定
 */
export function PersonaEditorPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { getPersonaById, addPersona, updatePersona } = usePersonaStore();
  const isNew = !id || id === "new";

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [personality, setPersonality] = useState("");
  const [scenario, setScenario] = useState("");
  const [greeting, setGreeting] = useState("");
  const [gender, setGender] = useState<PersonaGender>("female");
  const [tags, setTags] = useState("");

  // AI 生成状态
  const [isGenerating, setIsGenerating] = useState(false);
  const [generateTarget, setGenerateTarget] = useState<"all" | "personality" | null>(null);

  /** 加载已有角色数据 */
  useEffect(() => {
    if (!isNew && id) {
      const persona = getPersonaById(id);
      if (persona) {
        setName(persona.name);
        setDescription(persona.description);
        setPersonality(persona.personality);
        setScenario(persona.scenario);
        setGreeting(persona.greeting);
        setGender(persona.gender);
        setTags((persona.tags ?? []).join(", "));
      }
    }
  }, [id, isNew, getPersonaById]);

  /**
   * AI 辅助生成人格设定
   * 调用 LLM API 根据名称+描述自动填充性格/场景/开场白
   */
  const handleAIGenerate = async (target: "all" | "personality") => {
    if (!name.trim()) {
      alert("请先输入角色名称");
      return;
    }

    setIsGenerating(true);
    setGenerateTarget(target);

    try {
      // 动态导入 webApi（避免循环依赖）
      const { webStreamChat } = await import("@/lib/webApi");

      let generatedText = "";

      await webStreamChat(
        {
          personaId: "__ai_generate__",
          content: buildAIPrompt(name, description, gender, target),
        },
        {
          onToken: (token) => {
            generatedText += token;
          },
          onDone: () => {
            parseAndFill(generatedText, target);
            setIsGenerating(false);
            setGenerateTarget(null);
          },
          onError: (err) => {
            console.error("AI generate failed:", err);
            alert(`AI 生成失败：${err}\n\n请检查设置中的 AI 模型配置是否正确`);
            setIsGenerating(false);
            setGenerateTarget(null);
          },
        }
      );
    } catch (err) {
      console.error("AI generate error:", err);
      alert("AI 生成失败，请检查网络和 API 配置");
      setIsGenerating(false);
      setGenerateTarget(null);
    }
  };

  /** 构建 AI 提示词 */
  function buildAIPrompt(name: string, desc: string, gender: PersonaGender, target: "all" | "personality"): string {
    const genderLabel = gender === "female" ? "女性" : gender === "male" ? "男性" : "其他";

    if (target === "all") {
      return `你是一个专业的角色设定助手。请根据以下信息为这个角色生成完整的人格设定。

角色名称：${name}
${desc ? `描述：${desc}` : ""}
性别：${genderLabel}

请严格按照以下 JSON 格式输出（不要输出其他内容）：
{
  "personality": "详细的性格特征，包括说话风格、行为习惯、情绪特点等（100-200字中文）",
  "scenario": "角色所处的场景/世界观设定（50-100字中文）",
  "greeting": "角色的第一句开场白（20-50字中文，自然口语化）"
}`;
    }

    return `你是一个专业的角色设定助手。请根据以下信息为这个角色生成性格特征。

角色名称：${name}
${desc ? `描述：${desc}` : ""}
性别：${genderLabel}

请只输出 personality 字段的内容（100-200字中文），直接输出文本，不要包含JSON格式或其他标记。`;
  }

  /** 解析 AI 返回并填入表单 */
  function parseAndFill(text: string, target: "all" | "personality") {
    if (target === "personality") {
      // 纯文本模式，直接填入
      setPersonality(text.trim());
      return;
    }

    // 尝试解析 JSON
    try {
      // 找到 JSON 对象（可能被 markdown 包裹）
      const jsonMatch = text.match(/\{[\s\S]*\}/);
      if (!jsonMatch) throw new Error("No JSON found");

      const data = JSON.parse(jsonMatch[0]);
      if (data.personality) setPersonality(data.personality);
      if (data.scenario) setScenario(data.scenario);
      if (data.greeting) setGreeting(data.greeting);
    } catch {
      // JSON 解析失败，按段落分割填充
      const parts = text.split(/\n\n+/).filter(Boolean);
      if (parts[0]) setPersonality(parts[0].trim());
      if (parts[1]) setScenario(parts[1].trim());
      if (parts[2]) setGreeting(parts[2].trim());
    }
  }

  const handleSave = () => {
    if (!name.trim()) return;

    const tagList = tags.split(",").map((t) => t.trim()).filter(Boolean);

    if (isNew) {
      addPersona({
        name: name.trim(),
        avatar: "",
        description: description.trim(),
        personality: personality.trim(),
        scenario: scenario.trim(),
        greeting: greeting.trim(),
        gender,
        tags: tagList,
      });
    } else if (id) {
      updatePersona(id, {
        name: name.trim(),
        description: description.trim(),
        personality: personality.trim(),
        scenario: scenario.trim(),
        greeting: greeting.trim(),
        gender,
        tags: tagList,
      });
    }

    navigate(-1);
  };

  return (
    <PageContainer>
      <div className="flex items-center justify-between mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <Button onClick={handleSave} disabled={!name.trim()}>
          <Save className="h-4 w-4 mr-2" />
          保存
        </Button>
      </div>

      <h1 className="text-2xl font-bold text-[var(--color-card-foreground)] mb-6">
        {isNew ? "创建角色" : "编辑角色"}
      </h1>

      <div className="max-w-2xl space-y-4">
        {/* 名称 */}
        <div className="space-y-2">
          <label className="text-sm font-medium">名称 *</label>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="角色名称" />
        </div>

        {/* 性别 */}
        <div className="space-y-2">
          <label className="text-sm font-medium">性别</label>
          <div className="flex gap-2">
            {(["female", "male", "other"] as PersonaGender[]).map((g) => (
              <Button key={g} variant={gender === g ? "default" : "outline"} size="sm" onClick={() => setGender(g)}>
                {g === "female" ? "女" : g === "male" ? "男" : "其他"}
              </Button>
            ))}
          </div>
        </div>

        {/* 描述 */}
        <div className="space-y-2">
          <label className="text-sm font-medium">描述</label>
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder="角色背景、身份、外貌等描述" rows={2} />
        </div>

        {/* ══════════ 性格 + AI 生成按钮 ══════════ */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium">性格</label>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => handleAIGenerate("all")}
              disabled={isGenerating || !name.trim()}
              className="h-7 px-2 text-xs text-[var(--color-primary)] hover:bg-[var(--color-primary)]/10"
            >
              {isGenerating && generateTarget === "all" ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
              ) : (
                <Sparkles className="h-3.5 w-3.5 mr-1" />
              )}
              AI 生成全部设定
            </Button>
          </div>
          <Textarea value={personality} onChange={(e) => setPersonality(e.target.value)} placeholder="性格特征、说话风格、行为习惯等" rows={3} />
          {!personality && name.trim() && (
            <p className="text-[11px] text-[var(--color-muted-foreground)] flex items-center gap-1">
              <Sparkles className="h-3 w-3" />
              点击「AI 生成」根据角色名称自动生成完整的人格设定、场景和开场白
            </p>
          )}
        </div>

        {/* 场景 */}
        <div className="space-y-2">
          <label className="text-sm font-medium">场景</label>
          <Textarea value={scenario} onChange={(e) => setScenario(e.target.value)} placeholder="角色所处的场景或世界观" rows={2} />
        </div>

        {/* 开场白 */}
        <div className="space-y-2">
          <label className="text-sm font-medium">开场白</label>
          <Textarea value={greeting} onChange={(e) => setGreeting(e.target.value)} placeholder="角色对用户说的第一句话" rows={2} />
        </div>

        {/* 标签 */}
        <div className="space-y-2">
          <label className="text-sm font-medium">标签</label>
          <Input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="用逗号分隔，如：温柔,傲娇,学姐" />
        </div>
      </div>
    </PageContainer>
  );
}
