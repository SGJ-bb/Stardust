/**
 * 设置页共享小组件（仅 Settings 目录内部使用）
 */
import { Slider } from "@/components/ui/slider";
import type { ReactNode } from "react";

/** 分区标题（页面内的二级标题 + 说明） */
export function SectionTitle({ title, desc }: { title: string; desc?: string }) {
  return (
    <div className="pt-2">
      <h3 className="text-sm font-semibold text-[var(--color-card-foreground)]">{title}</h3>
      {desc && <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5">{desc}</p>}
    </div>
  );
}

/** 统一风格的下拉选择框 */
export function Select({
  value,
  onChange,
  options,
  className,
  disabled,
}: {
  value: string;
  onChange: (v: string) => void;
  options: Array<{ value: string; label: string }>;
  className?: string;
  disabled?: boolean;
}) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
      className={`w-full rounded-[var(--app-radius)] border border-[var(--color-border)] bg-[var(--color-card)] px-3 py-2 text-sm text-[var(--color-card-foreground)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-ring)] disabled:cursor-not-allowed disabled:opacity-50 ${className ?? ""}`}
    >
      {options.map((o) => (
        <option key={o.value} value={o.value}>
          {o.label}
        </option>
      ))}
    </select>
  );
}

/** 带说明文字的行（开关/控件 + 中文用途说明） */
export function SettingRow({
  title,
  desc,
  children,
}: {
  title: string;
  desc?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div className="min-w-0">
        <p className="text-sm font-medium text-[var(--color-card-foreground)]">{title}</p>
        {desc && <p className="text-xs text-[var(--color-muted-foreground)] mt-0.5 leading-snug">{desc}</p>}
      </div>
      <div className="shrink-0 flex items-center gap-2">{children}</div>
    </div>
  );
}

/**
 * 可编辑参数滑块：radix Slider 负责指针交互（受控值来自 store），
 * 键盘/精确输入走旁边的小数字框，避免 onChange 里回写自身 state 造成受控死循环。
 */
export function ParamSlider({
  label,
  hint,
  value,
  min,
  max,
  step,
  disabled,
  display,
  onChange,
}: {
  label: string;
  /** 厂商限制提示（paramHints / 不支持说明） */
  hint?: string;
  value: number;
  min: number;
  max: number;
  step: number;
  disabled?: boolean;
  display?: string;
  onChange: (v: number) => void;
}) {
  return (
    <div className={`space-y-1.5 ${disabled ? "opacity-50" : ""}`}>
      <div className="flex items-center justify-between gap-2">
        <label className="text-sm font-medium">{label}</label>
        <div className="flex items-center gap-2 shrink-0">
          <span className="text-xs tabular-nums text-[var(--color-muted-foreground)]">
            {display ?? value}
          </span>
          <input
            type="number"
            value={value}
            min={min}
            max={max}
            step={step}
            disabled={disabled}
            onChange={(e) => {
              const n = Number(e.target.value);
              if (!Number.isNaN(n)) onChange(Math.min(max, Math.max(min, n)));
            }}
            className="w-20 rounded-md border border-[var(--color-border)] bg-[var(--color-card)] px-1.5 py-0.5 text-xs tabular-nums focus-visible:outline-none"
          />
        </div>
      </div>
      <Slider
        value={[Math.min(max, Math.max(min, value))]}
        onValueChange={([v]) => { if (!disabled) onChange(v); }}
        min={min}
        max={max}
        step={step}
      />
      {hint && (
        <p className="text-[11px] text-[var(--color-muted-foreground)] leading-snug opacity-80">{hint}</p>
      )}
    </div>
  );
}
