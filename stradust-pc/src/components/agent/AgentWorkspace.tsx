// Agent 工作台主页面 — 左右分栏布局

import { useEffect } from 'react';
import { SkillPanel } from './SkillPanel';
import { AgentChat } from './AgentChat';
import { useAgentStore } from '@/stores/useAgentStore';

export function AgentWorkspace() {
  const fetchSkills = useAgentStore((s) => s.fetchSkills);
  const activeCategory = useAgentStore((s) => s.activeCategory);

  useEffect(() => {
    fetchSkills();
  }, [fetchSkills]);

  return (
    <div className="flex h-full w-full bg-background">
      {/* 左侧技能面板 */}
      <div className="w-[320px] border-r border-border/50 flex-shrink-0 overflow-hidden">
        <SkillPanel />
      </div>

      {/* 右侧对话区 */}
      <div className="flex-1 flex flex-col min-w-0">
        <AgentChat activeCategory={activeCategory} />
      </div>
    </div>
  );
}
