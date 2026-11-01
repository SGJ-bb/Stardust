/**
 * 工具执行确认通道 —— 借鉴 Operit 的工具权限三档（自动允许/每次询问/拒绝）。
 * ask 模式下插件执行前经总线向 UI 发起确认请求，等待用户决策（60s 超时视为拒绝）。
 */

import { emit } from "@/lib/events";

export interface ToolConfirmRequest {
  id: string;
  personaId: string;
  toolName: string;
  argsSummary: string;
}

export type ToolDecision = "allow" | "always" | "deny";

type Resolver = (d: ToolDecision) => void;

const pending = new Map<string, Resolver>();

/** UI 侧调用：发起确认请求，返回 Promise 等待用户决策 */
export function requestToolConfirm(req: ToolConfirmRequest, timeoutMs = 60_000): Promise<ToolDecision> {
  return new Promise((resolve) => {
    let settled = false;
    const settle = (d: ToolDecision) => {
      if (settled) return;
      settled = true;
      pending.delete(req.id);
      resolve(d);
    };
    pending.set(req.id, settle);
    emit("tool-confirm", req);
    setTimeout(() => settle("deny"), timeoutMs);
  });
}

/** UI 侧调用：用户点了按钮 */
export function resolveToolConfirm(id: string, decision: ToolDecision): void {
  pending.get(id)?.(decision);
}

export function hasPendingConfirms(): boolean {
  return pending.size > 0;
}
