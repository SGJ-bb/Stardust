/**
 * 屏幕感知 —— Android 无障碍屏幕识别（screen/ScreenRecognitionService.kt）的 Web 等价实现。
 *
 * Web 无 AccessibilityService，无法后台读屏；等价方案为
 * navigator.mediaDevices.getDisplayMedia 让用户主动授权截一帧，交给视觉模型分析。
 * 属于"用户显式授权 + 单次抓帧"模式，与 Android 常驻监听存在体验差异（隐私更保守）。
 */

import { useSettingsStore } from "@/stores/useSettingsStore";
import { visionAnalyze } from "./imagegen";

let cachedStream: MediaStream | null = null;

// ImageCapture 在部分 TS DOM lib 版本缺失：从 globalThis 取构造器，做最小结构兜底
interface ImageCaptureLike { grabFrame(): Promise<ImageBitmap> }
type ImageCaptureCtor = new (track: MediaStreamTrack) => ImageCaptureLike;
const ImageCaptureGlobal: ImageCaptureCtor | undefined =
  (globalThis as { ImageCapture?: ImageCaptureCtor }).ImageCapture;

async function ensureStream(): Promise<MediaStream> {
  // 会话内复用同一次授权（Android 服务常驻的近似），避免每轮对话都弹选择器
  if (cachedStream && cachedStream.active && cachedStream.getVideoTracks().some((t) => t.readyState === "live")) {
    return cachedStream;
  }
  cachedStream = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
  // 用户点击浏览器"停止共享"时清缓存
  cachedStream.getVideoTracks().forEach((t) => {
    t.onended = () => { cachedStream = null; };
  });
  return cachedStream;
}

async function grabViaVideo(track: MediaStreamTrack): Promise<ImageBitmap> {
  const stream = new MediaStream([track]);
  const video = document.createElement("video");
  video.srcObject = stream;
  video.muted = true;
  await video.play();
  // 等待出一帧有效画面
  await new Promise<void>((resolve) => {
    if (video.videoWidth > 0) return resolve();
    video.onloadeddata = () => resolve();
    setTimeout(resolve, 1500);
  });
  const bitmap = await createImageBitmap(video);
  video.pause();
  video.srcObject = null;
  return bitmap;
}

/** 截取当前屏幕一帧 → dataURL（PNG）；失败/取消返回 null */
export async function captureScreen(): Promise<string | null> {
  try {
    const stream = await ensureStream();
    const track = stream.getVideoTracks()[0];
    if (!track) return null;
    let frame: ImageBitmap;
    if (ImageCaptureGlobal) {
      // ImageCapture 路径（帧质量更好）
      frame = await new ImageCaptureGlobal(track).grabFrame();
    } else {
      frame = await grabViaVideo(track);
    }
    const canvas = document.createElement("canvas");
    canvas.width = frame.width;
    canvas.height = frame.height;
    const ctx = canvas.getContext("2d");
    if (!ctx) { frame.close?.(); return null; }
    ctx.drawImage(frame, 0, 0);
    frame.close?.();
    return canvas.toDataURL("image/png");
  } catch {
    return null; // 用户拒绝授权或环境不支持
  }
}

/** 是否开启屏幕感知（settings.aiFeatures.screenAwarenessEnabled，缺省视为关） */
export function isScreenAwarenessEnabled(): boolean {
  return useSettingsStore.getState().settings.aiFeatures?.screenAwarenessEnabled === true;
}

/**
 * 读取屏幕内容并回答（对应 Android ScreenRecognitionService 取窗口文本+截图识别）。
 * 开关关闭时返回提示文案而非静默。
 */
export async function readScreen(question?: string): Promise<string> {
  if (!isScreenAwarenessEnabled()) {
    return "屏幕感知未开启。请在 设置 → AI 功能 中启用「屏幕感知」后重试。";
  }
  const shot = await captureScreen();
  if (!shot) return "无法获取屏幕画面（用户取消了屏幕共享授权或浏览器不支持）。";
  try {
    return await visionAnalyze(shot, question?.trim() || "描述一下你现在看到的屏幕内容，简洁自然。");
  } catch (e) {
    return `屏幕识别失败：${(e as Error).message}`;
  }
}

/**
 * 给聊天引擎注入的"用户当前屏幕"上下文块（对应 MainActivity.buildSystemContext 的
 * 屏幕信息段）。失败/关闭/无新截图时返回空串，调用方可直接拼接。
 * 带短缓存（30s）避免每轮对话重复触发耗时的视觉调用。
 */
const BLOCK_CACHE_MS = 30 * 1000;
let lastBlock = "";
let lastBlockAt = 0;

export async function getScreenAwarenessBlock(): Promise<string> {
  if (!isScreenAwarenessEnabled()) return "";
  const now = Date.now();
  if (now - lastBlockAt < BLOCK_CACHE_MS) return lastBlock;
  try {
    const shot = await captureScreen();
    if (!shot) return "";
    const desc = await visionAnalyze(shot, "用一两句话概括用户屏幕上正在做什么，客观描述即可。");
    lastBlock = desc.trim() ? `\n【用户当前屏幕】${desc.trim()}` : "";
  } catch {
    lastBlock = "";
  }
  lastBlockAt = now;
  return lastBlock;
}

/** 释放屏幕共享轨道（退出应用/关闭功能时调用） */
export function releaseScreenCapture(): void {
  try {
    cachedStream?.getTracks().forEach((t) => t.stop());
  } catch { /* ignore */ }
  cachedStream = null;
  lastBlock = "";
  lastBlockAt = 0;
}
