import { useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { usePersonaStore } from "@/stores/usePersonaStore";
import type { PersonaGender } from "@/types/persona";
import { Sparkles, Loader2 } from "lucide-react";

interface CreatePersonaDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * 创建角色对话框 — 含 AI 辅助生成人格设定
 */
export function CreatePersonaDialog({ open, onOpenChange }: CreatePersonaDialogProps) {
  const { addPersona } = usePersonaStore();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [personality, setPersonality] = useState("");
  const [scenario, setScenario] = useState("");
  const [greeting, setGreeting] = useState("");
  const [gender, setGender] = useState<PersonaGender>("female");
  const [tags, setTags] = useState("");

  // AI 生成状态
  const [isGenerating, setIsGenerating] = useState(false);

  /** AI 辅助生成人格设定 */
  const handleAIGenerate = async () => {
    if (!name.trim()) {
      alert("请先输入角色名称");
      return;
    }

    setIsGenerating(true);
    try {
      const { webStreamChat } = await import("@/lib/webApi");
      let generatedText = "";

      await webStreamChat(
        {
          personaId: "__ai_generate__",
          content: buildAIPrompt(name, description, gender),
        },
        {
          onToken: (token) => { generatedText += token; },
          onDone: () => { parseAndFill(generatedText); setIsGenerating(false); },
          onError: (err) => {
            console.error("AI generate failed:", err);
            alert(`AI 生成失败：${err}`);
            setIsGenerating(false);
          },
        }
      );
    } catch (err) {
      console.error("AI generate error:", err);
      alert("AI 生成失败，请检查网络和 API 配置");
      setIsGenerating(false);
    }
  };

  function buildAIPrompt(name: string, desc: string, gender: PersonaGender): string {
    const genderLabel = gender === "female" ? "女性" : gender === "male" ? "男性" : "其他";
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

  function parseAndFill(text: string) {
    try {
      const jsonMatch = text.match(/\{[\s\S]*\}/);
      if (!jsonMatch) throw new Error("No JSON");
      const data = JSON.parse(jsonMatch[0]);
      if (data.personality) setPersonality(data.personality);
      if (data.scenario) setScenario(data.scenario);
      if (data.greeting) setGreeting(data.greeting);
    } catch {
      const parts = text.split(/\n\n+/).filter(Boolean);
      if (parts[0]) setPersonality(parts[0].trim());
      if (parts[1]) setScenario(parts[1].trim());
      if (parts[2]) setGreeting(parts[2].trim());
    }
  }

  const handleCreate = () => {
    if (!name.trim()) return;

    addPersona({
      name: name.trim(),
      avatar: "",
      description: description.trim(),
      personality: personality.trim(),
      scenario: scenario.trim(),
      greeting: greeting.trim(),
      gender,
      tags: tags.split(",").map((t) => t.trim()).filter(Boolean),
    });

    // 重置表单
    setName("");
    setDescription("");
    setPersonality("");
    setScenario("");
    setGreeting("");
    setGender("female");
    setTags("");
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>创建新角色</DialogTitle>
          <DialogDescription>设定角色的基本信息和性格</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* 名称 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">名称 *</label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="给角色取个名字"
            />
          </div>

          {/* 性别 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">性别</label>
            <div className="flex gap-2">
              {(["female", "male", "other"] as PersonaGender[]).map((g) => (
                <Button
                  key={g}
                  variant={gender === g ? "default" : "outline"}
                  size="sm"
                  onClick={() => setGender(g)}
                >
                  {g === "female" ? "女" : g === "male" ? "男" : "其他"}
                </Button>
              ))}
            </div>
          </div>

          {/* 描述 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">描述</label>
            <Textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="简要描述角色..."
              rows={2}
            />
          </div>

          {/* 性格 + AI 按钮 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-sm font-medium">性格</label>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleAIGenerate}
                disabled={isGenerating || !name.trim()}
                className="h-7 px-2 text-xs text-[var(--color-primary)] hover:bg-[var(--color-primary)]/10"
              >
                {isGenerating ? (
                  <Loader2 className="h-3.5 w-3.5 animate-spin mr-1" />
                ) : (
                  <Sparkles className="h-3.5 w-3.5 mr-1" />
                )}
                AI 生成
              </Button>
            </div>
            <Textarea
              value={personality}
              onChange={(e) => setPersonality(e.target.value)}
              placeholder="描述角色的性格特征..."
              rows={3}
            />
            {!personality && name.trim() && (
              <p className="text-[11px] text-[var(--color-muted-foreground)] flex items-center gap-1">
                <Sparkles className="h-3 w-3" />
                点击「AI 生成」根据角色名称自动生成人格设定
              </p>
            )}
          </div>

          {/* 场景 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">场景</label>
            <Textarea
              value={scenario}
              onChange={(e) => setScenario(e.target.value)}
              placeholder="描述角色所处的场景..."
              rows={2}
            />
          </div>

          {/* 开场白 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">开场白</label>
            <Textarea
              value={greeting}
              onChange={(e) => setGreeting(e.target.value)}
              placeholder="角色对用户说的第一句话..."
              rows={2}
            />
          </div>

          {/* 标签 */}
          <div className="space-y-2">
            <label className="text-sm font-medium">标签</label>
            <Input
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="用逗号分隔，如：可爱,温柔,治愈"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleCreate} disabled={!name.trim()}>
            创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
