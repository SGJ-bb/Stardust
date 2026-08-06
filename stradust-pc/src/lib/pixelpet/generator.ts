/**
 * 图片生成服务封装
 *
 * 负责调用用户配置的图片生成API来生成像素风格帧图。
 * 支持 OpenAI / Stability AI / ComfyUI / 通用SD WebUI API。
 */

import type { ImageGenConfig, GenerationProgressEvent } from './types';

// ─── 适配器接口 ───
interface GenAdapter {
  generate(prompt: string, config: ImageGenConfig): Promise<ArrayBuffer>;
}

class OpenAIAdapter implements GenAdapter {
  private apiUrl: string;
  private apiKey: string;
  private model: string;

  constructor(apiUrl: string, apiKey: string, model: string) {
    this.apiUrl = apiUrl;
    this.apiKey = apiKey;
    this.model = model;
  }

  async generate(prompt: string, _config: ImageGenConfig): Promise<ArrayBuffer> {
    const body = {
      model: this.model || 'dall-e-3',
      prompt,
      n: 1,
      size: '1024x1024', // DALL-E 最小也是这个，后续需要裁剪
      response_format: 'b64_json',
      quality: 'standard',
      style: 'natural', // 或 'vivid'
    };

    const resp = await fetch(this.apiUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.apiKey}`,
      },
      body: JSON.stringify(body),
    });

    if (!resp.ok) {
      const errText = await resp.text().catch(() => 'Unknown error');
      throw new Error(`OpenAI API error (${resp.status}): ${errText}`);
    }

    const data = await resp.json();
    // DALL-E 返回 base64
    const b64 = data.data?.[0]?.b64_json;
    if (!b64) throw new Error('No image data in OpenAI response');

    // base64 → ArrayBuffer
    const binaryStr = atob(b64);
    const bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) {
      bytes[i] = binaryStr.charCodeAt(i);
    }
    return bytes.buffer as ArrayBuffer;
  }
}

class GenericSDAdapter implements GenAdapter {
  private apiUrl: string;
  private apiKey: string;

  constructor(apiUrl: string, apiKey: string) {
    this.apiUrl = apiUrl;
    this.apiKey = apiKey;
  }

  async generate(prompt: string, config: ImageGenConfig): Promise<ArrayBuffer> {
    const body = {
      prompt,
      negative_prompt: '',
      width: parseInt(config.size.split('x')[0]) * 2, // SD通常需要更大尺寸
      height: parseInt(config.size.split('x')[1]) * 2,
      steps: config.steps,
      cfg_scale: config.cfgScale,
      sampler_name: 'euler_ancestral',
      seed: -1,
    };

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };
    if (this.apiKey) {
      headers['Authorization'] = `Bearer ${this.apiKey}`;
    }

    const resp = await fetch(`${this.apiUrl}/sdapi/v1/txt2img`, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });

    if (!resp.ok) {
      const errText = await resp.text().catch(() => 'Unknown error');
      throw new Error(`SD API error (${resp.status}): ${errText}`);
    }

    const data = await resp.json();
    // SD WebUI 返回 base64 图片数组
    const images: string[] = data.images || [];
    if (images.length === 0) throw new Error('No image in SD response');

    const b64 = images[0].split(',')[1] || images[0];
    const binaryStr = atob(b64);
    const bytes = new Uint8Array(binaryStr.length);
    for (let i = 0; i < binaryStr.length; i++) {
      bytes[i] = binaryStr.charCodeAt(i);
    }
    return bytes.buffer as ArrayBuffer;
  }
}

// ─── 创建适配器工厂 ───
function createAdapter(config: ImageGenConfig): GenAdapter {
  switch (config.provider) {
    case 'openai':
      return new OpenAIAdapter(
        config.apiUrl || 'https://api.openai.com/v1/images/generations',
        config.apiKey,
        config.model || 'dall-e-3'
      );
    case 'stability':
    case 'comfyui':
    case 'local_sd':
    case 'custom':
    default:
      return new GenericSDAdapter(config.apiUrl, config.apiKey);
  }
}

// ─── 提示词构建器 ───

/**
 * 构建单帧的完整提示词
 *
 * 公式: [基础描述] + [像素风格修饰] + [动作描述] + [帧序号描述]
 */
export function buildFramePrompt(
  basePrompt: string,
  actionPrompt: string,
  frameIndex: number,
  totalFrames: number,
  stylePrompt: string,
): string {
  // 帧序号描述 — 帮助AI理解动画连续性
  const frameHints = [
    'start of animation, initial pose',
    'transitioning into movement, early phase',
    'mid-action, peak of motion',
    'transitioning out of movement',
    'returning toward rest position',
    'near end of cycle, settling down',
  ];
  const hint = frameHints[Math.min(frameIndex, frameHints.length - 1)] ||
    `frame ${frameIndex + 1} of ${totalFrames}`;

  return [
    basePrompt.trim(),
    stylePrompt.trim(),
    actionPrompt.trim(),
    `${hint}, frame ${frameIndex + 1} of ${totalFrames}`,
  ].filter(Boolean).join(', ');
}

// ─── 批量生成服务 ───

/**
 * 批量生成一个动作的所有帧图
 *
 * @param config 图片生成配置
 * @param basePrompt 宠物基础描述
 * @param actionPrompt 动作专用提示词后缀
 * @param frameCount 总帧数
 * @param onProgress 进度回调
 * @returns 生成的帧数据数组 { index, data: ArrayBuffer }
 */
export async function generateActionFrames(
  config: ImageGenConfig,
  basePrompt: string,
  actionPrompt: string,
  frameCount: number,
  onProgress?: (event: GenerationProgressEvent) => void,
): Promise<{ frameIndex: number; data: ArrayBuffer }[]> {
  const adapter = createAdapter(config);
  const results: { frameIndex: number; data: ArrayBuffer }[] = [];

  // 并发控制：最多同时2个请求
  const concurrency = Math.min(config.batchSize > 1 ? 2 : 1, 2);

  for (let i = 0; i < frameCount; i += concurrency) {
    const batch = Array.from(
      { length: Math.min(concurrency, frameCount - i) },
      (_, j) => i + j
    );

    const batchResults = await Promise.allSettled(
      batch.map(async (idx) => {
        const prompt = buildFramePrompt(
          basePrompt,
          actionPrompt,
          idx,
          frameCount,
          config.stylePrompt,
        );

        onProgress?.({
          type: 'progress',
          actionId: '',
          currentFrame: idx + 1,
          totalFrames: frameCount,
          message: `正在生成第 ${idx + 1}/${frameCount} 帧...`,
        });

        try {
          const data = await adapter.generate(prompt, config);
          onProgress?.({
            type: 'frame_complete',
            actionId: '',
            currentFrame: idx + 1,
            totalFrames: frameCount,
            data: arrayBufferToBase64(data),
          });
          return { frameIndex: idx, data };
        } catch (err) {
          onProgress?.({
            type: 'frame_failed',
            actionId: '',
            currentFrame: idx + 1,
            totalFrames: frameCount,
            message: err instanceof Error ? err.message : String(err),
          });
          throw err;
        }
      })
    );

    for (const result of batchResults) {
      if (result.status === 'fulfilled') {
        results.push(result.value);
      }
    }
  }

  // 排序确保顺序正确
  results.sort((a, b) => a.frameIndex - b.frameIndex);

  onProgress?.({
    type: 'complete',
    actionId: '',
    currentFrame: frameCount,
    totalFrames: frameCount,
    message: '所有帧生成完成！',
  });

  return results;
}

// ═══ 工具函数 ═══

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}
