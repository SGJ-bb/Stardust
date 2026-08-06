// 工具调用过程可视化组件

import { useAgentStore } from '@/stores/useAgentStore';
import { Loader2, CheckCircle2, XCircle, Zap } from 'lucide-react';

/** 显示当前正在进行的工具调用 */
export function ToolCallDisplay() {
  const toolCalls = useAgentStore((s) => s.currentToolCalls);
  const isStreaming = useAgentStore((s) => s.isStreaming);

  if (toolCalls.length === 0 && !isStreaming) return null;

  return (
    <div className="space-y-2 my-2">
      {toolCalls.map((tc) => (
        <div
          key={tc.id}
          className={`flex items-start gap-3 p-3 rounded-xl border ${
            tc.status === 'success'
              ? 'border-emerald-500/20 bg-emerald-500/5'
              : tc.status === 'failed'
                ? 'border-red-500/20 bg-red-500/5'
                : 'border-blue-500/20 bg-blue-500/5 animate-pulse'
          }`}
        >
          {/* 图标 */}
          <div className={`mt-0.5 ${
            tc.status === 'running' ? 'animate-spin' : ''
          }`}>
            {tc.status === 'running' && <Loader2 className="w-4 h-4 text-blue-500" />}
            {tc.status === 'success' && <CheckCircle2 className="w-4 h-4 text-emerald-500" />}
            {tc.status === 'failed' && <XCircle className="w-4 h-4 text-red-500" />}
            {tc.status === 'pending' && <Zap className="w-4 h-4 text-yellow-500" />}
          </div>

          {/* 信息 */}
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-foreground">⚡ {tc.name}</span>
              <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${
                tc.status === 'running' ? 'bg-blue-500/15 text-blue-600'
                  : tc.status === 'success' ? 'bg-emerald-500/15 text-emerald-600'
                  : tc.status === 'failed' ? 'bg-red-500/15 text-red-500'
                    : 'bg-yellow-500/15 text-yellow-600'
              }`}>
                {tc.status === 'pending' ? '等待中'
                  : tc.status === 'running' ? '执行中...'
                    : tc.status === 'success' ? '成功' : '失败'}
              </span>
            </div>

            {/* 参数（折叠） */}
            {Object.keys(tc.arguments).length > 0 && (
              <details className="mt-1 group">
                <summary className="text-[10px] text-muted-foreground cursor-pointer hover:text-foreground">
                  查看参数 ({Object.keys(tc.arguments).length})
                </summary>
                <pre className="mt-1 text-[10px] bg-background/50 rounded p-2 overflow-x-auto text-muted-foreground">
                  {JSON.stringify(tc.arguments, null, 2)}
                </pre>
              </details>
            )}

            {/* 结果 */}
            {tc.result && (
              <pre className="mt-2 text-xs bg-background/70 rounded-lg p-2.5 overflow-x-auto max-h-24
                text-muted-foreground whitespace-pre-wrap leading-relaxed">
                {tc.result.length > 300 ? tc.result.slice(0, 300) + '...' : tc.result}
              </pre>
            )}
          </div>
        </div>
      ))}

      {/* 流式等待指示器（无具体工具时） */}
      {isStreaming && toolCalls.length === 0 && (
        <div className="flex items-center gap-2 p-2 text-xs text-muted-foreground animate-pulse">
          <Zap className="w-3.5 h-3.5" />
          正在分析需求并选择合适的技能...
        </div>
      )}
    </div>
  );
}
