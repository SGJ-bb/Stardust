import { useState, useEffect, useCallback } from "react";
import { usePixelPetStore } from "@/stores/usePixelPetStore";
import { usePixelPet } from "@/hooks/usePixelPet";
import { PetCreator } from "./PetCreator";
import { ActionEditor } from "./ActionEditor";
import { PixelPetSettings } from "./PixelPetSettings";
import type { PetAction, PixelPet } from "@/lib/pixelpet/types";
import {
  Box,
  Plus,
  Trash2,
  Star,
  Settings2,
  Sparkles,
  Monitor,
  ChevronRight,
  X,
  Image as ImageIcon,
} from "lucide-react";

/**
 * 像素宠物管理面板
 *
 * 集成到设置页面的完整管理界面：
 * - 宠物列表（创建/删除/激活/切换模式）
 * - API配置（PixelPetSettings）
 * - 动作编辑器（ActionEditor）
 * - 创建向导（PetCreator）
 */
export function PixelPetManagePanel() {
  const store = usePixelPetStore();
  const {
    pets,
    activePet,
    currentPetActions,
    petMode,
    isGenerating,
    loadPets,
    setActivePet,
    deletePet,
    setPetMode,
  } = store;

  const { createPet, createBuiltinActions } = usePixelPet();

  // 子面板状态
  const [showCreator, setShowCreator] = useState(false);
  const [selectedAction, setSelectedAction] = useState<PetAction | null>(null);
  const [showApiConfig, setShowApiConfig] = useState(false);

  useEffect(() => {
    loadPets();
  }, [loadPets]);

  // ═══ 创建宠物完成回调 ═══
  const handlePetCreated = useCallback(
    async (pet: PixelPet) => {
      setShowCreator(false);
      // 自动创建内置动作
      await createBuiltinActions(pet.id, ["idle", "walk", "jump", "happy", "sleep", "wave"]);
      // 自动设为活跃
      await setActivePet(pet.id);
    },
    [createBuiltinActions, setActivePet]
  );

  // ═══ 删除宠物 ═══
  const handleDeletePet = async (pet: PixelPet) => {
    if (!confirm(`确定删除宠物「${pet.name}」吗？相关动作和帧图也将被删除。`)) return;
    await deletePet(pet.id);
  };

  // ═══ 切换宠物模式 ═══
  const handleToggleMode = () => {
    const newMode = petMode === "pixel" ? "live2d" : "pixel";
    setPetMode(newMode);
  };

  // ═══ 渲染子面板 ═══
  if (showCreator) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowCreator(false)}
            className="p-1.5 rounded-lg hover:bg-white/5 text-gray-400 hover:text-white transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
          <h3 className="text-sm font-semibold">创建像素宠物</h3>
        </div>
        <PetCreator onComplete={handlePetCreated} />
      </div>
    );
  }

  if (selectedAction) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setSelectedAction(null)}
            className="p-1.5 rounded-lg hover:bg-white/5 text-gray-400 hover:text-white transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
          <h3 className="text-sm font-semibold">
            编辑动作: {selectedAction.displayName}
          </h3>
        </div>
        <ActionEditor action={selectedAction} />
      </div>
    );
  }

  if (showApiConfig) {
    return (
      <div className="space-y-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowApiConfig(false)}
            className="p-1.5 rounded-lg hover:bg-white/5 text-gray-400 hover:text-white transition-colors"
          >
            <X className="h-4 w-4" />
          </button>
          <h3 className="text-sm font-semibold">图片生成API配置</h3>
        </div>
        <PixelPetSettings />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* ===== 模式切换 ===== */}
      <div className="glass-card p-5 rounded-[var(--radius-lg)]">
        <div className="flex items-center justify-between mb-3">
          <div>
            <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
              桌宠渲染模式
            </h3>
            <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
              选择悬浮窗使用的渲染方式
            </p>
          </div>
          <span
            className={`text-xs font-medium px-2.5 py-1 rounded-full ${
              petMode === "pixel"
                ? "bg-green-500/15 text-green-400"
                : "bg-blue-500/15 text-blue-400"
            }`}
          >
            {petMode === "pixel" ? "像素宠物" : "Live2D"}
          </span>
        </div>
        <div className="grid grid-cols-2 gap-2">
          {[
            {
              key: "live2d",
              label: "Live2D",
              desc: "高精度角色模型",
              icon: Monitor,
              color: petMode === "live2d" ? "blue" : "gray",
            },
            {
              key: "pixel",
              label: "像素宠物",
              desc: "自定义动画精灵",
              icon: Sparkles,
              color: petMode === "pixel" ? "green" : "gray",
            },
          ].map((mode) => {
            const Icon = mode.icon;
            const isActive = petMode === mode.key;
            return (
              <button
                key={mode.key}
                onClick={handleToggleMode}
                className={`relative flex items-center gap-2.5 px-3.5 py-3 rounded-lg border transition-all duration-200 ${
                  isActive
                    ? `border-${mode.color}-500/50 bg-${mode.color}-500/10`
                    : "border-[var(--color-border)] hover:border-[var(--color-muted-foreground)]"
                }`}
              >
                <Icon className={`h-4 w-4 shrink-0 ${isActive ? "text-green-400" : "text-gray-500"}`} />
                <div className="text-left">
                  <div className={`text-xs font-semibold ${isActive ? "text-[var(--color-card-foreground)]" : "text-gray-400"}`}>
                    {mode.label}
                  </div>
                  <div className="text-[10px] opacity-60">{mode.desc}</div>
                </div>
                {isActive && (
                  <div className="absolute bottom-1 left-1/2 -translate-x-1/2 w-5 h-0.5 rounded-full bg-green-400" />
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* ===== 宠物列表 ===== */}
      <div className="glass-card p-5 rounded-[var(--radius-lg)]">
        <div className="flex items-center justify-between mb-4">
          <div>
            <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
              我的宠物
            </h3>
            <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
              管理你的像素宠物，点击设为当前使用
            </p>
          </div>
          <button
            onClick={() => setShowCreator(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs bg-primary hover:bg-primary/80 text-white rounded-lg transition-colors"
          >
            <Plus className="h-3.5 w-3.5" />
            新建宠物
          </button>
        </div>

        {pets.length === 0 ? (
          <div className="text-center py-10 border border-dashed border-[var(--color-border)] rounded-lg">
            <ImageIcon className="h-8 w-8 mx-auto text-gray-600 mb-2" />
            <p className="text-sm text-gray-400 mb-1">还没有创建任何宠物</p>
            <p className="text-xs text-gray-600 mb-3">点击上方按钮创建你的第一个像素宠物</p>
            <button
              onClick={() => setShowCreator(true)}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-xs bg-white/5 hover:bg-white/10 border border-[var(--color-border)] rounded-lg text-gray-300 transition-colors"
            >
              <Sparkles className="h-3.5 w-3.5" />
              开始创建
            </button>
          </div>
        ) : (
          <div className="space-y-2">
            {pets.map((pet) => {
              const isActive = activePet?.id === pet.id;
              const actionCount = store.currentPetActions.length > 0 && isActive
                ? store.currentPetActions.length
                : "?";

              return (
                <div
                  key={pet.id}
                  className={`group flex items-center gap-3 p-3 rounded-lg border transition-all duration-200 cursor-pointer ${
                    isActive
                      ? "border-green-500/40 bg-green-500/5"
                      : "border-[var(--color-border)] hover:border-[var(--color-muted-foreground)] hover:bg-white/[0.02]"
                  }`}
                  onClick={() => setActivePet(pet.id)}
                >
                  {/* 缩略图 */}
                  <div className="w-12 h-12 rounded-lg bg-black/30 flex items-center justify-center overflow-hidden shrink-0 image-pixelated">
                    {pet.referenceImagePath ? (
                      <img
                        src={pet.referenceImagePath}
                        alt={pet.name}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <Box className="h-5 w-5 text-gray-600" />
                    )}
                  </div>

                  {/* 信息 */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium truncate">{pet.name}</span>
                      {isActive && (
                        <span className="shrink-0 text-[9px] px-1.5 py-0.5 rounded bg-green-500/20 text-green-400 font-medium">
                          使用中
                        </span>
                      )}
                    </div>
                    <div className="flex items-center gap-2 mt-0.5 text-[10px] text-gray-500">
                      <span>{pet.spriteWidth}x{pet.spriteHeight}px</span>
                      <span>·</span>
                      <span>{actionCount} 个动作</span>
                      <span>·</span>
                      <span>{pet.fps}fps</span>
                    </div>
                  </div>

                  {/* 操作按钮 */}
                  <div className="flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setActivePet(pet.id);
                      }}
                      className="p-1.5 rounded-md hover:bg-white/10 text-gray-400 hover:text-yellow-400 transition-colors"
                      title="设为当前宠物"
                    >
                      <Star className="h-3.5 w-3.5" />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeletePet(pet);
                      }}
                      className="p-1.5 rounded-md hover:bg-white/10 text-gray-400 hover:text-red-400 transition-colors"
                      title="删除宠物"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ===== 当前宠物的动作列表（仅在有活跃宠物时显示）===== */}
      {activePet && currentPetActions.length > 0 && (
        <div className="glass-card p-5 rounded-[var(--radius-lg)]">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">
                「{activePet.name}」的动作列表
              </h3>
              <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                点击编辑动作或生成帧图
              </p>
            </div>
            <ChevronRight className="h-4 w-4 text-gray-600" />
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
            {currentPetActions.map((action) => (
              <button
                key={action.id}
                onClick={() => setSelectedAction(action)}
                className="flex flex-col items-start gap-1.5 p-3 rounded-lg border border-[var(--color-border)] hover:border-[var(--color-primary)]/50 hover:bg-[var(--color-primary)]/5 transition-all text-left"
              >
                <span className="text-xs font-medium truncate w-full">{action.displayName}</span>
                <div className="flex items-center gap-2 text-[10px] text-gray-500">
                  <span>{action.frames.length}/{action.frameCount}帧</span>
                  <span
                    className={`px-1 rounded ${
                      action.loopMode === "loop"
                        ? "bg-blue-500/15 text-blue-400"
                        : action.loopMode === "pingpong"
                          ? "bg-purple-500/15 text-purple-400"
                          : "bg-orange-500/15 text-orange-400"
                    }`}
                  >
                    {action.loopMode}
                  </span>
                </div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* ===== API 配置入口 ===== */}
      <div className="glass-card p-5 rounded-[var(--radius-lg)]">
        <button
          onClick={() => setShowApiConfig(true)}
          className="w-full flex items-center justify-between group"
        >
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-[var(--color-muted)] flex items-center justify-center">
              <Settings2 className="h-4 w-4 text-[var(--color-primary)]" />
            </div>
            <div className="text-left">
              <h3 className="text-sm font-semibold text-[var(--color-card-foreground)] group-hover:text-[var(--color-primary)] transition-colors">
                图片生成API配置
              </h3>
              <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">
                配置AI绘图接口（OpenAI / SD WebUI / ComfyUI）
              </p>
            </div>
          </div>
          <ChevronRight className="h-4 w-4 text-gray-600 group-hover:text-gray-400 transition-colors" />
        </button>
      </div>

      {/* 生成中提示 */}
      {isGenerating && (
        <div className="rounded-lg p-3 bg-blue-500/10 border border-blue-500/20 text-xs text-blue-300 flex items-center gap-2">
          <Sparkles className="h-3.5 w-3.5 animate-pulse" />
          正在生成帧图片，请稍候...
        </div>
      )}
    </div>
  );
}
