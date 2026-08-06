import { useState, useCallback, useEffect } from "react";
import { usePixelPetStore } from "@/stores/usePixelPetStore";
import { Play, Pause, Trash2, Plus, RefreshCw, Download, Image } from "lucide-react";
import type { PetAction, LoopMode, GenerationProgressEvent } from "@/lib/pixelpet/types";
import { generateActionFrames } from "@/lib/pixelpet/generator";

interface ActionEditorProps {
  /** 要编辑的动作 */
  action: PetAction;
  /** 保存回调 */
  onSave?: (action: PetAction) => void;
  /** 删除回调 */
  onDelete?: () => void;
  /** 取消回调 */
  onCancel?: () => void;
  /** 只读模式（内置动作限制编辑） */
  readOnly?: boolean;
}

/**
 * 动作编辑器
 *
 * 支持编辑动作属性、预览动画、生成/重新生成帧图、单帧操作
 */
export function ActionEditor({ action, onSave, onDelete, onCancel, readOnly = false }: ActionEditorProps) {
  const { updateAction, genConfig, isGenerating, setGenerating, setGenerationProgress } = usePixelPetStore();
  const [editingName, setEditingName] = useState(action.name);
  const [editingDisplayName, setEditingDisplayName] = useState(action.displayName);
  const [editingPrompt, setEditingPrompt] = useState(action.prompt);
  const [editingFrameCount, setEditingFrameCount] = useState(action.frameCount);
  const [editingLoopMode, setEditingLoopMode] = useState<LoopMode>(action.loopMode);
  const [editingFrameDuration, setEditingFrameDuration] = useState(action.frameDuration);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentPreviewIdx, setCurrentPreviewIdx] = useState(0);
  const [progress, setProgress] = useState<GenerationProgressEvent | null>(null);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);

  // 预览动画播放定时器
  useEffect(() => {
    if (!isPlaying || action.frames.length === 0) return;
    const interval = setInterval(() => {
      setCurrentPreviewIdx((prev) => (prev + 1) % action.frames.length);
    }, editingFrameDuration);
    return () => clearInterval(interval);
  }, [isPlaying, action.frames.length, editingFrameDuration]);

  // 标记有未保存修改
  useEffect(() => {
    const changed =
      editingName !== action.name ||
      editingDisplayName !== action.displayName ||
      editingPrompt !== action.prompt ||
      editingFrameCount !== action.frameCount ||
      editingLoopMode !== action.loopMode ||
      editingFrameDuration !== action.frameDuration;
    setHasUnsavedChanges(changed);
  }, [editingName, editingDisplayName, editingPrompt, editingFrameCount, editingLoopMode, editingFrameDuration, action]);

  // ═══ 保存修改 ═══
  const handleSave = useCallback(() => {
    const updated: PetAction = {
      ...action,
      name: editingName,
      displayName: editingDisplayName,
      prompt: editingPrompt,
      frameCount: editingFrameCount,
      loopMode: editingLoopMode,
      frameDuration: editingFrameDuration,
    };
    updateAction(updated);
    onSave?.(updated);
    setHasUnsavedChanges(false);
  }, [action, editingName, editingDisplayName, editingPrompt, editingFrameCount, editingLoopMode, editingFrameDuration, updateAction, onSave]);

  // ═══ 生成帧图 ═══
  const handleGenerate = useCallback(async () => {
    if (!genConfig.apiUrl || !genConfig.apiKey) {
      alert('请先在设置中配置图片生成API');
      return;
    }

    setGenerating(true);
    setProgress(null);

    try {
      const activePet = usePixelPetStore.getState().activePet;
      if (!activePet) throw new Error('没有活跃的宠物');

      // 如果帧数变化了，需要先更新动作记录
      if (editingFrameCount !== action.frameCount) {
        handleSave();
      }

      const results = await generateActionFrames(
        genConfig,
        activePet.basePrompt,
        editingPrompt,
        editingFrameCount,
        (event) => {
          setProgress(event);
          setGenerationProgress(event);
        },
      );

      // 更新每帧的状态
      for (const result of results) {
        const { invoke } = await import('@tauri-apps/api/core');
        // 保存图片文件并更新帧记录
        await invoke('save_generated_frame', {
          frameData: result.data,
          petId: activePet.id,
          actionId: action.id,
          frameIndex: result.frameIndex,
          prompt: '',
        });
      }

      // 重新加载动作数据
      const { loadPetActions } = usePixelPetStore.getState();
      loadPetActions(activePet.id);
    } catch (err) {
      console.error('帧生成失败:', err);
      setProgress({
        type: 'error',
        actionId: action.id,
        currentFrame: 0,
        totalFrames: editingFrameCount,
        message: err instanceof Error ? err.message : String(err),
      });
    } finally {
      setGenerating(false);
    }
  }, [genConfig, action, editingPrompt, editingFrameCount, handleSave]);

  // 获取当前预览帧
  const currentFrame = action.frames[currentPreviewIdx];
  const readyFrames = action.frames.filter((f) => f.status === 'ready');

  return (
    <div className="flex flex-col gap-4 p-4 max-w-lg">
      {/* 头部：动作名称 + 操作 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          {readOnly && <span className="text-[10px] bg-blue-500/20 text-blue-400 px-1.5 py-0.5 rounded">内置</span>}
          <input
            value={editingDisplayName}
            onChange={(e) => setEditingDisplayName(e.target.value)}
            disabled={readOnly}
            className="bg-transparent text-lg font-medium text-white border-b border-transparent focus:border-primary outline-none disabled:opacity-70"
          />
          <span className="text-xs text-gray-500">({editingName})</span>
        </div>
        <div className="flex items-center gap-1">
          {hasUnsavedChanges && !readOnly && (
            <button onClick={handleSave} className="p-1.5 text-green-400 hover:bg-green-400/10 rounded transition-colors" title="保存修改">
              <Download className="h-3.5 w-3.5" />
            </button>
          )}
          {!readOnly && onDelete && (
            <button onClick={onDelete} className="p-1.5 text-red-400 hover:bg-red-400/10 rounded transition-colors" title="删除动作">
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          )}
          {onCancel && (
            <button onClick={onCancel} className="p-1.5 text-gray-400 hover:bg-white/5 rounded transition-colors" title="关闭">
              ✕
            </button>
          )}
        </div>
      </div>

      <div className="flex gap-4">
        {/* 左侧：参数编辑 */}
        <div className="flex-1 flex flex-col gap-3">
          {/* 提示词 */}
          <div>
            <label className="text-[10px] text-gray-500 mb-1 block">动作提示词</label>
            <textarea
              value={editingPrompt}
              onChange={(e) => setEditingPrompt(e.target.value)}
              disabled={readOnly}
              rows={2}
              className="w-full bg-black/20 border border-white/5 rounded-lg px-2.5 py-1.5 text-xs text-white placeholder:text-gray-600 focus:border-primary outline-none resize-none disabled:opacity-50"
              placeholder="描述这个动作的样子..."
            />
          </div>

          {/* 参数行 */}
          <div className="flex gap-3">
            <div className="flex-1">
              <label className="text-[10px] text-gray-500 mb-1 block">帧数</label>
              <select
                value={editingFrameCount}
                onChange={(e) => setEditingFrameCount(Number(e.target.value))}
                disabled={readOnly}
                className="w-full bg-black/20 border border-white/5 rounded-lg px-2 py-1.5 text-xs text-white disabled:opacity-50"
              >
                {[2, 3, 4, 6, 8, 12].map((n) => (
                  <option key={n} value={n}>{n} 帧</option>
                ))}
              </select>
            </div>
            <div className="flex-1">
              <label className="text-[10px] text-gray-500 mb-1 block">循环模式</label>
              <select
                value={editingLoopMode}
                onChange={(e) => setEditingLoopMode(e.target.value as LoopMode)}
                disabled={readOnly}
                className="w-full bg-black/20 border border-white/5 rounded-lg px-2 py-1.5 text-xs text-white disabled:opacity-50"
              >
                <option value="loop">循环</option>
                <option value="once">一次</option>
                <option value="pingpong">往返</option>
              </select>
            </div>
            <div className="flex-1">
              <label className="text-[10px] text-gray-500 mb-1 block">帧间隔(ms)</label>
              <input
                type="number"
                value={editingFrameDuration}
                onChange={(e) => setEditingFrameDuration(Number(e.target.value))}
                disabled={readOnly}
                className="w-full bg-black/20 border border-white/5 rounded-lg px-2 py-1.5 text-xs text-white disabled:opacity-50"
                min={30}
                max={1000}
                step={25}
              />
            </div>
          </div>

          {/* 生成按钮 */}
          <button
            onClick={handleGenerate}
            disabled={isGenerating || !genConfig.apiUrl}
            className="w-full py-2 text-xs bg-primary/20 hover:bg-primary/30 disabled:bg-gray-800 disabled:text-gray-600 text-primary rounded-lg transition-colors flex items-center justify-center gap-1.5"
          >
            {isGenerating ? (
              <><RefreshCw className="h-3 w-3 animate-spin" /> 生成中...</>
            ) : (
              <><Plus className="h-3 w-3" /> 生成帧图 ({editingFrameCount}帧)</>
            )}
          </button>

          {/* 进度显示 */}
          {(progress || isGenerating) && (
            <div className="text-[10px] space-y-1">
              {progress && (
                <>
                  <div className="flex justify-between text-gray-400">
                    <span>{progress.message}</span>
                    <span>{progress.currentFrame}/{progress.totalFrames}</span>
                  </div>
                  <div className="w-full bg-gray-800 rounded-full h-1">
                    <div
                      className="bg-primary h-1 rounded-full transition-all"
                      style={{ width: `${(progress.currentFrame / progress.totalFrames) * 100}%` }}
                    />
                  </div>
                </>
              )}
            </div>
          )}
        </div>

        {/* 右侧：预览区 */}
        <div className="w-[160px] flex flex-col gap-2">
          {/* 帧预览画布 */}
          <div className="aspect-square bg-black/30 rounded-lg border border-white/5 flex items-center justify-center overflow-hidden relative">
            {currentFrame && currentFrame.status === 'ready' ? (
              <img
                src={currentFrame.imagePath}
                alt={`帧 ${currentPreviewIdx}`}
                className="max-w-full max-h-full object-contain"
                style={{ imageRendering: 'pixelated' }}
              />
            ) : (
              <div className="text-center text-gray-600 text-[10px] p-2">
                <Image className="h-6 w-6 mx-auto mb-1 opacity-30" />
                {action.frames.length > 0
                  ? `等待生成 (${readyFrames.length}/${action.frames.length})`
                  : '暂无帧图'}
              </div>
            )}
          </div>

          {/* 播放控制 */}
          <div className="flex items-center gap-1">
            <button
              onClick={() => setIsPlaying(!isPlaying)}
              disabled={readyFrames.length === 0}
              className="flex-1 py-1 text-[10px] bg-white/5 hover:bg-white/10 disabled:opacity-30 rounded transition-colors flex items-center justify-center gap-1"
            >
              {isPlaying ? <><Pause className="h-3 w-3" /> 暂停</> : <><Play className="h-3 w-3" /> 播放</>}
            </button>
            <span className="text-[10px] text-gray-600">{currentPreviewIdx + 1}/{action.frames.length}</span>
          </div>

          {/* 帧缩略图列表 */}
          {action.frames.length > 0 && (
            <div className="flex gap-0.5 overflow-x-auto pb-1">
              {action.frames.map((frame, i) => (
                <button
                  key={frame.id}
                  onClick={() => { setCurrentPreviewIdx(i); setIsPlaying(false); }}
                  className={`w-8 h-8 rounded border flex-shrink-0 overflow-hidden ${
                    i === currentPreviewIdx ? 'border-primary' : 'border-white/10'
                  } ${frame.status === 'ready' ? '' : 'opacity-30'}`}
                >
                  {frame.status === 'ready' ? (
                    <img src={frame.imagePath} alt="" className="w-full h-full object-cover" style={{ imageRendering: 'pixelated' }} />
                  ) : (
                    <div className="w-full h-full bg-gray-800 flex items-center justify-center text-[8px] text-gray-600">
                      {frame.status === 'failed' ? '!' : i + 1}
                    </div>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
