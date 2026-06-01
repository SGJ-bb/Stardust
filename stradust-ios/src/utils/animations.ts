/**
 * 星尘 iOS - 动画工具模块
 * 基于 Anime.js v4 + Impeccable 动效原则
 */

import { animate, stagger, createTimeline } from "animejs";

// ── 缓动曲线（Impeccable: 禁止 bounce/elastic） ──
export const EASING = {
  outExpo: "outExpo",
  outQuart: "outQuart",
  inOutQuart: "inOutQuart",
  outCubic: "outCubic",
  inOutCubic: "inOutCubic",
  spring: "outBack",
} as const;

// ── 时长 ──
export const DURATION = {
  instant: 100,
  fast: 200,
  normal: 350,
  slow: 500,
  slower: 700,
} as const;

// ── 交错延迟 ──
export const STAGGER = {
  fast: 30,
  normal: 60,
  slow: 100,
} as const;

/**
 * 淡入上移动画
 */
export function fadeInUp(
  targets: string | Element | Element[],
  delay: number = 0
) {
  return animate(targets, {
    opacity: [0, 1],
    translateY: [16, 0],
    duration: DURATION.normal,
    delay: delay,
    ease: EASING.outExpo,
  });
}

/**
 * 淡入缩放动画
 */
export function fadeInScale(
  targets: string | Element | Element[],
  delay: number = 0
) {
  return animate(targets, {
    opacity: [0, 1],
    scale: [0.92, 1],
    duration: DURATION.normal,
    delay: delay,
    ease: EASING.outExpo,
  });
}

/**
 * 交错淡入动画
 */
export function staggerFadeIn(
  targets: string | Element | Element[],
  staggerDelay: number = STAGGER.normal
) {
  return animate(targets, {
    opacity: [0, 1],
    translateY: [12, 0],
    duration: DURATION.normal,
    delay: stagger(staggerDelay),
    ease: EASING.outExpo,
  });
}

/**
 * 列表项交错入场
 */
export function listStaggerIn(
  targets: string | Element | Element[]
) {
  return animate(targets, {
    opacity: [0, 1],
    translateY: [20, 0],
    scale: [0.96, 1],
    duration: DURATION.normal,
    delay: stagger(STAGGER.normal, { start: 50 }),
    ease: EASING.outExpo,
  });
}

/**
 * 底部弹窗入场
 */
export function bottomSheetIn(targets: string | Element | Element[]) {
  return animate(targets, {
    translateY: ["100%", "0%"],
    opacity: [0.5, 1],
    duration: DURATION.slow,
    ease: EASING.outExpo,
  });
}

/**
 * 底部弹窗退场
 */
export function bottomSheetOut(targets: string | Element | Element[]) {
  return animate(targets, {
    translateY: ["0%", "100%"],
    opacity: [1, 0.5],
    duration: DURATION.normal,
    ease: EASING.outCubic,
  });
}

/**
 * 页面滑入（从右侧）
 */
export function pageSlideIn(targets: string | Element | Element[]) {
  return animate(targets, {
    translateX: ["100%", "0%"],
    opacity: [0, 1],
    duration: DURATION.slow,
    ease: EASING.outExpo,
  });
}

/**
 * 页面滑出（向右侧）
 */
export function pageSlideOut(targets: string | Element | Element[]) {
  return animate(targets, {
    translateX: ["0%", "100%"],
    opacity: [1, 0],
    duration: DURATION.normal,
    ease: EASING.outCubic,
  });
}

/**
 * 消息气泡入场
 */
export function messageBubbleIn(
  targets: string | Element | Element[],
  isUser: boolean = false
) {
  return animate(targets, {
    opacity: [0, 1],
    translateX: isUser ? [24, 0] : [-24, 0],
    scale: [0.9, 1],
    duration: DURATION.normal,
    ease: EASING.outExpo,
  });
}

/**
 * 按钮按压反馈
 */
export function buttonPress(targets: string | Element | Element[]) {
  return animate(targets, {
    scale: [1, 0.96, 1],
    duration: 200,
    ease: EASING.outCubic,
  });
}

/**
 * 心跳/呼吸动画
 */
export function breathe(targets: string | Element | Element[]) {
  return animate(targets, {
    scale: [1, 1.05, 1],
    opacity: [0.8, 1, 0.8],
    duration: 3000,
    ease: EASING.inOutCubic,
    loop: true,
  });
}

/**
 * 浮动动画
 */
export function float(targets: string | Element | Element[]) {
  return animate(targets, {
    translateY: [0, -6, 0],
    duration: 3000,
    ease: EASING.inOutCubic,
    loop: true,
  });
}

/**
 * 发光脉冲
 */
export function glowPulse(targets: string | Element | Element[]) {
  return animate(targets, {
    boxShadow: [
      "0 0 8px rgba(139, 108, 255, 0.15)",
      "0 0 24px rgba(139, 108, 255, 0.3)",
      "0 0 8px rgba(139, 108, 255, 0.15)",
    ],
    duration: 2000,
    ease: EASING.inOutCubic,
    loop: true,
  });
}

/**
 * 打字指示器动画
 */
export function typingDots(targets: string | Element | Element[]) {
  return animate(targets, {
    opacity: [0.3, 1, 0.3],
    scale: [0.8, 1, 0.8],
    duration: 1400,
    delay: stagger(200),
    ease: EASING.inOutCubic,
    loop: true,
  });
}

/**
 * 数字滚动动画
 */
export function countUp(
  targets: string | Element | Element[],
  from: number,
  to: number
) {
  return animate(targets, {
    textContent: [from, to],
    duration: DURATION.slow,
    ease: EASING.outExpo,
    round: 1,
  });
}

/**
 * 进度条动画
 */
export function progressFill(
  targets: string | Element | Element[],
  from: number,
  to: number
) {
  return animate(targets, {
    width: [`${from}%`, `${to}%`],
    duration: DURATION.slow,
    ease: EASING.outExpo,
  });
}

/**
 * 涟漪效果
 */
export function ripple(
  targets: string | Element | Element[],
  originX: string = "50%",
  originY: string = "50%"
) {
  return animate(targets, {
    scale: [0, 4],
    opacity: [0.6, 0],
    duration: 600,
    ease: EASING.outQuart,
  });
}

/**
 * 创建时间线动画
 */
export function createAnimTimeline() {
  return createTimeline({
    defaults: {
      duration: DURATION.normal,
      ease: EASING.outExpo,
    },
  });
}
