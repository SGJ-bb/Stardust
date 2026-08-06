// ══════════════════════════════════════════════
// 像素宠物类型定义 (Pixel Pet Types)
// ══════════════════════════════════════════════

// ─── 渲染配置 ───
export interface PixelRenderConfig {
  spriteWidth: number;       // 精灵宽度(px)
  spriteHeight: number;      // 精灵高度(px)
  fps: number;               // 播放帧率
  scale: number;             // 显示缩放倍数
  renderMode: 'pixel_perfect' | 'smooth';
}

export const DEFAULT_RENDER_CONFIG: PixelRenderConfig = {
  spriteWidth: 64,
  spriteHeight: 64,
  fps: 8,
  scale: 3.0,
  renderMode: 'pixel_perfect',
};

// ─── 像素宠物 ───
// 注意：Rust后端返回扁平字段(sprite_width/sprite_height/fps/scale/render_mode)，
// 此处同时保留扁平字段和计算属性renderConfig以兼容两端
export interface PixelPet {
  id: string;
  name: string;
  description?: string;
  referenceImagePath?: string;   // 对应 Rust reference_image_path (Tauri自动转为camelCase)
  basePrompt: string;           // 对应 base_prompt
  negativePrompt?: string;      // 对应 negative_prompt
  // 扁平渲染配置字段 (与Rust模型一一对应)
  spriteWidth: number;          // 对应 sprite_width
  spriteHeight: number;         // 对应 sprite_height
  fps: number;                  // 对应 fps
  scale: number;                // 对应 scale
  renderMode: string;           // 对应 render_mode ('pixel_perfect' | 'smooth')
  isActive: boolean;             // 对应 is_active
  createdAt: number;             // 对应 created_at
  updatedAt: number;             // 对应 updated_at
  actionIds?: string[];         // 前端衍生字段，Rust不返回
}

/** 从扁平PixelPet构建嵌套renderConfig */
export function getRenderConfig(pet: PixelPet): PixelRenderConfig {
  return {
    spriteWidth: pet.spriteWidth || 64,
    spriteHeight: pet.spriteHeight || 64,
    fps: pet.fps || 8,
    scale: pet.scale || 3.0,
    renderMode: (pet.renderMode as PixelRenderConfig['renderMode']) || 'pixel_perfect',
  };
}

// ─── 循环模式 ───
export type LoopMode = 'loop' | 'once' | 'pingpong';

// ─── 动作定义 ───
export interface PetAction {
  id: string;
  petId: string;
  name: string;           // key: idle, walk, jump...
  displayName: string;    // 显示名: 待机, 行走...
  description?: string;
  prompt: string;         // 该动作的提示词后缀
  frameCount: number;
  frameDuration: number;  // 每帧ms
  loopMode: LoopMode;
  isBuiltin: boolean;
  triggerEvents?: string[];
  sortOrder: number;
  frames: PixelFrame[];
  createdAt: number;
}

// ─── 单帧 ───
export type FrameStatus = 'generating' | 'ready' | 'failed';

export interface PixelFrame {
  id: string;
  actionId: string;
  frameIndex: number;       // 对应 Rust frame_index
  imagePath: string;
  imageHash?: string;
  promptUsed: string;
  status: FrameStatus;
  generatedAt?: number;
}

// ─── 图片生成配置 ───
// 注意：与Rust PixelGenConfig 保持扁平结构一致（Tauri自动 snake_case → camelCase）
export type ImageGenProvider = 'openai' | 'stability' | 'comfyui' | 'custom' | 'local_sd';

export interface ImageGenConfig {
  provider: ImageGenProvider;
  apiUrl?: string;          // 对应 api_url
  apiKey?: string;           // 对应 api_key
  model?: string;            // 对应 model
  stylePrompt: string;       // 对应 style_prompt
  size: string;              // 对应 size ("64x64")
  steps: number;             // 对应 steps
  cfgScale: number;          // 对应 cfg_scale
  batchSize: number;         // 对应 batch_size
}

export const DEFAULT_GEN_CONFIG: ImageGenConfig = {
  provider: 'custom',
  apiUrl: '',
  apiKey: '',
  model: '',
  stylePrompt:
    'pixel art, 16-bit style, retro game sprite, clean black outline, solid color fill, no anti-aliasing, transparent background',
  size: '64x64',
  steps: 20,
  cfgScale: 7.0,
  batchSize: 1,
};

// ─── 内置默认动作定义 ───
export const BUILTIN_ACTIONS: Omit<PetAction, 'id' | 'petId' | 'frames' | 'createdAt'>[] = [
  {
    name: 'idle',
    displayName: '待机',
    prompt: 'standing still, breathing gently, subtle movement, neutral expression',
    frameCount: 4,
    frameDuration: 150,
    loopMode: 'loop',
    isBuiltin: true,
    triggerEvents: [],
    sortOrder: 0,
  },
  {
    name: 'walk',
    displayName: '行走',
    prompt: 'walking animation, legs moving in walking cycle, arms swinging naturally',
    frameCount: 6,
    frameDuration: 120,
    loopMode: 'loop',
    isBuiltin: true,
    triggerEvents: [],
    sortOrder: 1,
  },
  {
    name: 'run',
    displayName: '跑步',
    prompt: 'running fast, dynamic pose, motion blur effect, energetic',
    frameCount: 6,
    frameDuration: 80,
    loopMode: 'loop',
    isBuiltin: true,
    triggerEvents: [],
    sortOrder: 2,
  },
  {
    name: 'jump',
    displayName: '跳跃',
    prompt: 'jumping in the air, legs tucked up, happy excited expression',
    frameCount: 6,
    frameDuration: 100,
    loopMode: 'once',
    isBuiltin: true,
    triggerEvents: ['interaction_happy'],
    sortOrder: 3,
  },
  {
    name: 'sit',
    displayName: '坐下',
    prompt: 'sitting down on the ground, relaxed posture, looking forward',
    frameCount: 4,
    frameDuration: 150,
    loopMode: 'loop',
    isBuiltin: true,
    triggerEvents: ['rest'],
    sortOrder: 4,
  },
  {
    name: 'sleep',
    displayName: '睡觉',
    prompt: 'sleeping peacefully, eyes closed, zzz bubbles floating above head',
    frameCount: 4,
    frameDuration: 200,
    loopMode: 'loop',
    isBuiltin: true,
    triggerEvents: ['long_idle'],
    sortOrder: 5,
  },
  {
    name: 'happy',
    displayName: '开心',
    prompt: 'very happy and excited, jumping with joy, sparkles around, big smile',
    frameCount: 4,
    frameDuration: 100,
    loopMode: 'once',
    isBuiltin: true,
    triggerEvents: ['chat_positive'],
    sortOrder: 6,
  },
  {
    name: 'sad',
    displayName: '难过',
    prompt: 'sad and downcast, drooping posture, tears in eyes, unhappy',
    frameCount: 4,
    frameDuration: 150,
    loopMode: 'once',
    isBuiltin: true,
    triggerEvents: ['chat_negative'],
    sortOrder: 7,
  },
  {
    name: 'angry',
    displayName: '生气',
    prompt: 'angry and frustrated, puffed cheeks, steam coming from ears or head',
    frameCount: 4,
    frameDuration: 100,
    loopMode: 'once',
    isBuiltin: true,
    triggerEvents: ['chat_angry'],
    sortOrder: 8,
  },
  {
    name: 'surprised',
    displayName: '惊讶',
    prompt: 'surprised and shocked, wide open eyes, jaw dropped, exclamation mark above',
    frameCount: 4,
    frameDuration: 100,
    loopMode: 'once',
    isBuiltin: true,
    triggerEvents: ['sudden_message'],
    sortOrder: 9,
  },
  {
    name: 'wave',
    displayName: '招手',
    prompt: 'waving hand hello or goodbye, friendly gesture, smiling warmly',
    frameCount: 4,
    frameDuration: 120,
    loopMode: 'once',
    isBuiltin: true,
    triggerEvents: ['greeting'],
    sortOrder: 10,
  },
  {
    name: 'dance',
    displayName: '跳舞',
    prompt: 'dancing fun moves, grooving to music, lively and rhythmic poses',
    frameCount: 8,
    frameDuration: 120,
    loopMode: 'loop',
    isBuiltin: true,
    triggerEvents: ['celebration'],
    sortOrder: 11,
  },
];

// ─── 生成进度事件 ───
export interface GenerationProgressEvent {
  type: 'progress' | 'frame_complete' | 'frame_failed' | 'complete' | 'error';
  actionId: string;
  currentFrame: number;
  totalFrames: number;
  message?: string;
  data?: string; // base64 image data for frame_complete
}

// ─── 宠物模式 ───
export type PetMode = 'live2d' | 'pixel';
