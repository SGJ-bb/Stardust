// 技能面板 — 分类Tab + 搜索 + 技能卡片网格

import { useState, useMemo } from 'react';
import {
  FileText, Film, Terminal, Sparkles,
  Search, CheckCircle2, Circle, Loader2,
} from 'lucide-react';
import { useAgentStore } from '@/stores/useAgentStore';
import type { SkillMeta, SkillCategory, CategoryTab } from '@/lib/agent/types';
import { SKILL_CATEGORY_META } from '@/lib/agent/types';

const ALL_TAB: CategoryTab = { key: 'all' as const, label: '全部', icon: 'LayoutGrid', count: 0 };

export function SkillPanel() {
  const skills = useAgentStore((s) => s.skills);
  const loading = useAgentStore((s) => s.skillsLoading);
  const activeCategory = useAgentStore((s) => s.activeCategory);
  const setActiveCategory = useAgentStore((s) => s.setActiveCategory);
  const toggleSkill = useAgentStore((s) => s.toggleSkill);

  const [searchQuery, setSearchQuery] = useState('');

  // 构建分类Tab列表
  const tabs = useMemo<CategoryTab[]>(() => {
    const categoryKeys: (SkillCategory | 'all')[] = ['all', 'office', 'media', 'dev', 'ai_assistant'];
    return categoryKeys.map((key) => ({
      key,
      label: key === 'all' ? '全部' : SKILL_CATEGORY_META[key as SkillCategory]?.label ?? key,
      icon: key === 'all' ? 'LayoutGrid' : SKILL_CATEGORY_META[key as SkillCategory]?.icon ?? 'Box',
      count: key === 'all'
        ? skills.length
        : skills.filter((s) => s.category === key).length,
    }));
  }, [skills]);

  // 过滤技能
  const filteredSkills = useMemo(() => {
    let result = skills;
    if (activeCategory !== 'all') {
      result = result.filter((s) => s.category === activeCategory);
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(
        (s) =>
          s.name.toLowerCase().includes(q) ||
          s.description.toLowerCase().includes(q) ||
          s.id.toLowerCase().includes(q)
      );
    }
    return result;
  }, [skills, activeCategory, searchQuery]);

  // 获取分类图标组件
  const CategoryIcon = ({ name }: { name: string }) => {
    switch (name) {
      case 'FileText': return <FileText className="w-4 h-4" />;
      case 'Film': return <Film className="w-4 h-4" />;
      case 'Terminal': return <Terminal className="w-4 h-4" />;
      case 'Sparkles': return <Sparkles className="w-4 h-4" />;
      default: return <LayoutGrid className="w-4 h-4" />;
    }
  };

  return (
    <div className="flex flex-col h-full bg-card/30">
      {/* 标题 + 搜索 */}
      <div className="p-4 border-b border-border/50">
        <h2 className="text-sm font-semibold mb-3 flex items-center gap-2">
          <Sparkles className="w-4 h-4 text-primary" />
          技能工作台
        </h2>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-muted-foreground" />
          <input
            type="text"
            placeholder="搜索技能..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-2 text-sm bg-background rounded-lg border border-border/50
              focus:outline-none focus:ring-1 focus:ring-primary/50 focus:border-primary/50
              placeholder:text-muted-foreground"
          />
        </div>
      </div>

      {/* 分类标签栏 */}
      <div className="flex gap-1 p-3 pt-2 overflow-x-auto scrollbar-none">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveCategory(tab.key)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all whitespace-nowrap
              ${activeCategory === tab.key
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-secondary text-secondary-foreground hover:bg-secondary/80'
              }`}
          >
            <CategoryIcon name={tab.icon} />
            {tab.label}
            <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${
              activeCategory === tab.key
                ? 'bg-primary-foreground/20'
                : 'bg-background/50'
            }`}>
              {tab.count}
            </span>
          </button>
        ))}
      </div>

      {/* 技能卡片网格 */}
      <div className="flex-1 overflow-y-auto p-3 pt-2">
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : filteredSkills.length === 0 ? (
          <div className="text-center py-12 text-muted-foreground text-sm">
            <p>没有找到匹配的技能</p>
            <p className="text-xs mt-1">试试其他关键词或分类</p>
          </div>
        ) : (
          <div className="grid gap-2">
            {filteredSkills.map((skill) => (
              <SkillCard
                key={skill.id}
                skill={skill}
                onToggle={() => toggleSkill(skill.id)}
              />
            ))}
          </div>
        )}
      </div>

      {/* 底部状态栏 */}
      <div className="p-3 border-t border-border/50 text-[10px] text-muted-foreground text-center">
        已启用 {skills.filter((s) => s.enabled).length} / {skills.length} 个技能
      </div>
    </div>
  );
}

/** 单个技能卡片 */
function SkillCard({ skill, onToggle }: { skill: SkillMeta; onToggle: () => void }) {
  const catColor: Record<SkillCategory, string> = {
    office: 'text-blue-500',
    media: 'text-purple-500',
    dev: 'text-green-500',
    ai_assistant: 'text-orange-500',
  };
  const catBg: Record<SkillCategory, string> = {
    office: 'bg-blue-500/10',
    media: 'bg-purple-500/10',
    dev: 'bg-green-500/10',
    ai_assistant: 'bg-orange-500/10',
  };

  return (
    <div
      className={`group p-3 rounded-xl border border-border/50 bg-card/50 hover:bg-card hover:border-border
        transition-all cursor-pointer ${skill.enabled ? '' : 'opacity-60'}`}
      onClick={onToggle}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className={`font-medium text-sm ${catColor[skill.category]}`}>
              {skill.name}
            </span>
            {skill.enabled ? (
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500 flex-shrink-0" />
            ) : (
              <Circle className="w-3.5 h-3.5 text-muted-foreground/40 flex-shrink-0" />
            )}
          </div>
          <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
            {skill.description}
          </p>

          {/* CLI依赖提示 */}
          {skill.cliDeps.length > 0 && (
            <div className="flex gap-1 mt-2 flex-wrap">
              {skill.cliDeps.map((dep) => (
                <span
                  key={dep}
                  className={`inline-flex items-center gap-1 text-[10px] px-1.5 py-0.5 rounded-md ${catBg[skill.category]} ${catColor[skill.category]}`}
                >
                  {dep}
                </span>
              ))}
            </div>
          )}
        </div>

        {/* 开关按钮 */}
        <button
          className={`flex-shrink-0 w-8 h-5 rounded-full transition-colors relative ${
            skill.enabled ? 'bg-primary' : 'bg-muted'
          }`}
          onClick={(e) => {
            e.stopPropagation();
            onToggle();
          }}
        >
          <span
            className={`absolute top-0.5 w-4 h-4 rounded-full bg-white shadow-sm transition-transform ${
              skill.enabled ? 'translate-x-[18px]' : 'translate-x-0.5'
            }`}
          />
        </button>
      </div>
    </div>
  );
}

// 用于未匹配的图标兜底
function LayoutGrid({ className }: { className?: string }) {
  return (
    <svg className={className ?? ''} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" />
      <rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" />
    </svg>
  );
}
