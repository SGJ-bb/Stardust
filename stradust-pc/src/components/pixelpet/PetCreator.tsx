import { useState, useCallback } from "react";
import { usePixelPetStore } from "@/stores/usePixelPetStore";
import {
  Upload,
  Sparkles,
  ChevronRight,
  ChevronLeft,
  Check,
} from "lucide-react";
import type { PixelPet } from "@/lib/pixelpet/types";
import { BUILTIN_ACTIONS } from "@/lib/pixelpet/types";

interface PetCreatorProps {
  /** 创建完成回调 */
  onComplete?: (pet: PixelPet) => void;
  /** 取消回调 */
  onCancel?: () => void;
}

type Step = "upload" | "info" | "actions" | "confirm";

/**
 * 宠物创建向导
 *
 * Step 1: 上传参考图片
 * Step 2: 设定基础信息(名称/描述/提示词)
 * Step 3: 选择初始动作
 * Step 4: 确认并创建
 */
export function PetCreator({ onComplete, onCancel }: PetCreatorProps) {
  const { createPet, createAction, genConfig } = usePixelPetStore();
  const [step, setStep] = useState<Step>("upload");
  const [previewUrl, setPreviewUrl] = useState<string>("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [basePrompt, setBasePrompt] = useState("");
  const [negativePrompt, setNegativePrompt] = useState("");
  const [selectedActions, setSelectedActions] = useState<string[]>([
    "idle",
    "happy",
    "sleep",
    "wave",
  ]);
  const [isCreating, setIsCreating] = useState(false);
  const [createdPet, setCreatedPet] = useState<PixelPet | null>(null);

  // ── Step 1: 上传图片 ──
  const handleImageUpload = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (!file) return;

      const reader = new FileReader();
      reader.onload = () => {
        setPreviewUrl(reader.result as string);
      };
      reader.readAsDataURL(file);
    },
    [],
  );

  const handleDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0];
    if (!file || !file.type.startsWith("image/")) return;

    const reader = new FileReader();
    reader.onload = () => {
      setPreviewUrl(reader.result as string);
    };
    reader.readAsDataURL(file);
  }, []);

  // ── Step 2 → 3: 选择动作 ──
  const toggleAction = useCallback((actionName: string) => {
    setSelectedActions((prev) =>
      prev.includes(actionName)
        ? prev.filter((n) => n !== actionName)
        : [...prev, actionName],
    );
  }, []);

  // ── Step 4: 确认创建 ──
  const handleCreate = useCallback(async () => {
    if (!name.trim() || !basePrompt.trim()) return;
    setIsCreating(true);

    try {
      const pet = await createPet({
        name: name.trim(),
        description: description.trim() || undefined,
        referenceImagePath: previewUrl || undefined,
        basePrompt: basePrompt.trim(),
        negativePrompt: negativePrompt.trim() || undefined,
      });
      setCreatedPet(pet);

      // 创建选中的内置动作 (使用内置模板)
      const selectedTemplates = BUILTIN_ACTIONS.filter((a) =>
        selectedActions.includes(a.name),
      );
      for (const template of selectedTemplates) {
        await createAction({
          petId: pet.id,
          name: template.name,
          displayName: template.displayName,
          description: template.description,
          prompt: template.prompt,
          frameCount: template.frameCount,
          frameDuration: template.frameDuration,
          loopMode: template.loopMode,
          triggerEvents: template.triggerEvents,
        });
      }

      onComplete?.(pet);
    } catch (err) {
      console.error("创建宠物失败:", err);
    } finally {
      setIsCreating(false);
    }
  }, [
    name,
    description,
    basePrompt,
    negativePrompt,
    previewUrl,
    selectedActions,
    createPet,
    createAction,
    onComplete,
  ]);

  // 检查是否配置了图片生成API
  const hasGenConfig = !!(genConfig.apiUrl && genConfig.apiKey);

  const steps: { key: Step; label: string }[] = [
    { key: "upload", label: "上传图片" },
    { key: "info", label: "基础信息" },
    { key: "actions", label: "选择动作" },
    { key: "confirm", label: "确认创建" },
  ];
  const stepIndex = steps.findIndex((s) => s.key === step);

  return (
    <div className="flex flex-col gap-4 p-4 max-w-lg">
      {/* 步骤指示器 */}
      <div className="flex items-center gap-2 text-xs">
        {steps.map((s, i) => (
          <span key={s.key} className="flex items-center gap-1">
            <span
              className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-medium ${
                i <= stepIndex
                  ? "bg-primary text-white"
                  : "bg-gray-700 text-gray-400"
              }`}
            >
              {i < stepIndex ? <Check className="h-3 w-3" /> : i + 1}
            </span>
            <span className={i <= stepIndex ? "text-primary" : "text-gray-500"}>
              {s.label}
            </span>
            {i < steps.length - 1 && (
              <ChevronRight className="h-3 w-3 text-gray-600" />
            )}
          </span>
        ))}
      </div>

      {/* Step 1: 上传参考图片 */}
      {step === "upload" && (
        <div
          className="border-2 border-dashed border-gray-600 rounded-xl p-8 text-center cursor-pointer hover:border-primary transition-colors"
          onDrop={handleDrop}
          onDragOver={(e) => e.preventDefault()}
          onClick={() => document.getElementById("pet-image-input")?.click()}
        >
          <input
            id="pet-image-input"
            type="file"
            accept="image/*"
            className="hidden"
            onChange={handleImageUpload}
          />
          {previewUrl ? (
            <div className="space-y-3">
              <img
                src={previewUrl}
                alt="预览"
                className="w-32 h-32 object-cover mx-auto rounded-lg image-pixelated"
              />
              <p className="text-xs text-gray-400">点击重新选择</p>
            </div>
          ) : (
            <div className="space-y-2">
              <Upload className="h-8 w-8 mx-auto text-gray-500" />
              <p className="text-sm text-gray-400">拖拽或点击上传参考图片</p>
              <p className="text-xs text-gray-600">支持 JPG / PNG / WebP</p>
            </div>
          )}
        </div>
      )}

      {/* Step 2: 基础信息 */}
      {step === "info" && (
        <div className="flex flex-col gap-3">
          {previewUrl && (
            <img
              src={previewUrl}
              alt=""
              className="w-16 h-16 object-cover rounded-lg"
            />
          )}
          <div>
            <label className="text-xs text-gray-400 mb-1 block">
              宠物名称 *
            </label>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="如：小橙猫、像素龙"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none"
            />
          </div>
          <div>
            <label className="text-xs text-gray-400 mb-1 block">描述</label>
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="简短描述（可选）"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none"
            />
          </div>
          <div>
            <label className="text-xs text-gray-400 mb-1 block">
              基础提示词 *
            </label>
            <textarea
              value={basePrompt}
              onChange={(e) => setBasePrompt(e.target.value)}
              placeholder="用英文描述你的宠物外观，如: a cute orange cat with big eyes, fluffy tail"
              rows={3}
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none resize-none"
            />
          </div>
          <div>
            <label className="text-xs text-gray-400 mb-1 block">
              反向提示词
            </label>
            <input
              value={negativePrompt}
              onChange={(e) => setNegativePrompt(e.target.value)}
              placeholder="不希望出现的内容（可选）"
              className="w-full bg-black/30 border border-white/10 rounded-lg px-3 py-2 text-sm text-white placeholder:text-gray-600 focus:border-primary outline-none"
            />
          </div>
        </div>
      )}

      {/* Step 3: 选择初始动作 */}
      {step === "actions" && (
        <div className="flex flex-col gap-2 max-h-[300px] overflow-y-auto">
          <p className="text-xs text-gray-400">选择要生成的初始动作：</p>
          <div className="grid grid-cols-2 gap-2">
            {BUILTIN_ACTIONS.map((action) => (
              <button
                key={action.name}
                onClick={() => toggleAction(action.name)}
                className={`text-left px-3 py-2 rounded-lg text-xs transition-all ${
                  selectedActions.includes(action.name)
                    ? "bg-primary/20 border border-primary text-primary"
                    : "bg-black/20 border border-white/5 text-gray-400 hover:border-white/20"
                }`}
              >
                <div className="font-medium">{action.displayName}</div>
                <div className="text-[10px] opacity-60">
                  {action.frameCount}帧 · {action.loopMode}
                </div>
              </button>
            ))}
          </div>
          {!hasGenConfig && (
            <p className="text-xs text-yellow-500/80 mt-2">
              尚未配置图片生成API，创建后需要先在设置中配置才能生成帧图。
            </p>
          )}
        </div>
      )}

      {/* Step 4: 确认 */}
      {step === "confirm" && (
        <div className="flex flex-col gap-3">
          <div className="bg-black/20 rounded-lg p-3 space-y-2">
            <div className="flex items-center gap-3">
              {previewUrl && (
                <img
                  src={previewUrl}
                  alt=""
                  className="w-12 h-12 rounded-lg object-cover"
                />
              )}
              <div>
                <div className="font-medium text-sm">{name || "未命名"}</div>
                <div className="text-xs text-gray-400">
                  {description || "无描述"}
                </div>
              </div>
            </div>
            <div className="text-xs text-gray-500 line-clamp-2">
              {basePrompt}
            </div>
            <div className="flex flex-wrap gap-1">
              {selectedActions.map((name) => {
                const tpl = BUILTIN_ACTIONS.find((a) => a.name === name);
                return (
                  <span
                    key={name}
                    className="text-[10px] bg-primary/15 text-primary px-1.5 py-0.5 rounded"
                  >
                    {tpl?.displayName || name}
                  </span>
                );
              })}
            </div>
          </div>

          {!hasGenConfig && (
            <div className="text-xs text-yellow-500 bg-yellow-500/10 rounded-lg p-2">
              警告：尚未配置图片生成API。创建宠物后请前往设置页面配置API，然后为每个动作生成帧图。
            </div>
          )}
        </div>
      )}

      {/* 导航按钮 */}
      <div className="flex justify-between pt-2">
        <button
          onClick={() => {
            if (step === "upload") {
              onCancel?.();
              return;
            }
            const idx = stepIndex;
            setStep(steps[idx - 1]?.key || "upload");
          }}
          className="px-4 py-2 text-sm text-gray-400 hover:text-white transition-colors"
        >
          {step === "upload" ? "取消" : "上一步"}
        </button>
        <button
          onClick={() => {
            if (step === "confirm") {
              handleCreate();
              return;
            }
            setStep(steps[stepIndex + 1]?.key || "confirm");
          }}
          disabled={
            (step === "info" && !name.trim()) ||
            (step === "info" && !basePrompt.trim()) ||
            isCreating
          }
          className="px-4 py-2 text-sm bg-primary hover:bg-primary/80 disabled:bg-gray-700 disabled:text-gray-500 text-white rounded-lg transition-colors flex items-center gap-1.5"
        >
          {isCreating ? (
            <>创建中...</>
          ) : step === "confirm" ? (
            <>
              <Sparkles className="h-3.5 w-3.5" /> 开始创建
            </>
          ) : (
            "下一步"
          )}
        </button>
      </div>
    </div>
  );
}
