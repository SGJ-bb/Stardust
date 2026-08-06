import { usePixelPetStore } from "@/stores/usePixelPetStore";
import { Loader2, CheckCircle, XCircle, Image } from "lucide-react";
import type { GenerationProgressEvent } from "@/lib/pixelpet/types";

interface GenerationProgressProps {
  /** 自定义额外className */
  className?: string;
}

/**
 * 生成进度面板
 *
 * 显示当前帧图生成的实时进度，包括：
 * - 总体进度条
 * - 每帧状态（生成中/完成/失败）
 * - 错误信息
 */
export function GenerationProgress({ className }: GenerationProgressProps) {
  const { isGenerating, generationProgress } = usePixelPetStore();

  if (!isGenerating && !generationProgress) return null;

  const progress = generationProgress;
  const percent = progress ? (progress.currentFrame / progress.totalFrames) * 100 : 0;

  return (
    <div className={`rounded-lg bg-black/40 border border-white/5 p-3 ${className || ''}`}>
      {/* 标题行 */}
      <div className="flex items-center gap-2 mb-2">
        {progress?.type === 'complete' ? (
          <CheckCircle className="h-4 w-4 text-green-400" />
        ) : progress?.type === 'error' ? (
          <XCircle className="h-4 w-4 text-red-400" />
        ) : (
          <Loader2 className="h-4 w-4 text-primary animate-spin" />
        )}
        <span className="text-xs font-medium text-white">
          {progress?.message || (isGenerating ? '正在生成帧图...' : '')}
        </span>
      </div>

      {/* 进度条 */}
      {isGenerating && progress && progress.type !== 'complete' && (
        <div className="w-full bg-gray-800 rounded-full h-1.5 mb-2">
          <div
            className="bg-primary h-1.5 rounded-full transition-all duration-300 ease-out"
            style={{ width: `${percent}%` }}
          />
        </div>
      )}

      {/* 帧网格 */}
      {progress && progress.totalFrames > 0 && (
        <div className="flex flex-wrap gap-1">
          {Array.from({ length: progress.totalFrames }, (_, i) => {
            let status: 'pending' | 'active' | 'done' | 'failed' = 'pending';
            if (i < progress.currentFrame - 1) {
              status = progress.type === 'frame_failed' && i === progress.currentFrame - 1 ? 'failed' : 'done';
            } else if (i === progress.currentFrame - 1 && isGenerating) {
              status = 'active';
            }

            return (
              <div
                key={i}
                className={`w-5 h-5 rounded flex items-center justify-center text-[9px] ${
                  status === 'done'
                    ? 'bg-green-500/20 text-green-400 border border-green-500/30'
                    : status === 'active'
                    ? 'bg-primary/20 text-primary border border-primary/30 animate-pulse'
                    : status === 'failed'
                    ? 'bg-red-500/20 text-red-400 border border-red-500/30'
                    : 'bg-gray-800 text-gray-600 border border-gray-700'
                }`}
                title={`帧 ${i + 1}`}
              >
                {status === 'done' ? (
                  <CheckCircle className="h-3 w-3" />
                ) : status === 'failed' ? (
                  <XCircle className="h-3 w-3" />
                ) : status === 'active' ? (
                  <Loader2 className="h-3 w-3 animate-spin" />
                ) : (
                  i + 1
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 完成或错误信息 */}
      {progress?.type === 'complete' && (
        <p className="text-xs text-green-400 mt-2">
          全部 {progress.totalFrames} 帧已生成完成！
        </p>
      )}
      {progress?.type === 'error' && progress.message && (
        <p className="text-xs text-red-400 mt-2">{progress.message}</p>
      )}
    </div>
  );
}
