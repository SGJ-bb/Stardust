/**
 * 提示词模板管理
 *
 * 内置默认动作的提示词模板、像素风格修饰符等。
 */

import type { PetAction } from './types';
import { BUILTIN_ACTIONS } from './types';

/** 默认像素风格修饰符 */
export const DEFAULT_PIXEL_STYLE_MODIFIER =
  'pixel art, 16-bit retro game sprite, clean black outline, solid color fill, no anti-aliasing, transparent background, front-facing view, simple details, vibrant colors';

/**
 * 获取内置默认动作列表（不含 id/petId 等运行时字段）
 */
export function getBuiltinActions(): Omit<PetAction, 'id' | 'petId' | 'frames' | 'createdAt'>[] {
  return BUILTIN_ACTIONS;
}

/**
 * 根据动作名获取对应的提示词模板
 */
export function getActionPromptTemplate(actionName: string): string {
  const found = BUILTIN_ACTIONS.find((a) => a.name === actionName);
  return found?.prompt || '';
}

/**
 * 根据触发事件获取推荐的动作名列表
 */
export function getActionsForEvent(eventName: string): string[] {
  const matched: string[] = [];
  for (const action of BUILTIN_ACTIONS) {
    if (action.triggerEvents?.includes(eventName)) {
      matched.push(action.name);
    }
  }
  return matched;
}

/** 所有可用的触发事件名 */
export const TRIGGER_EVENTS = [
  'interaction_happy',
  'rest',
  'long_idle',
  'chat_positive',
  'chat_negative',
  'chat_angry',
  'sudden_message',
  'greeting',
  'celebration',
] as const;
