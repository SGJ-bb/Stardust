/**
 * 像素宠物 Hook
 *
 * 封装像素宠物的常用操作：
 * - 加载/创建/删除宠物
 * - 动作管理
 * - 图片生成
 * - 模式切换
 */

import { useCallback, useEffect } from 'react';
import { usePixelPetStore } from '@/stores/usePixelPetStore';
import type { PetAction, ImageGenConfig, GenerationProgressEvent, PixelPet } from '@/lib/pixelpet/types';
import { DEFAULT_RENDER_CONFIG, getRenderConfig } from '@/lib/pixelpet/types';
import { generateActionFrames, buildFramePrompt } from '@/lib/pixelpet/generator';

export function usePixelPet() {
  const store = usePixelPetStore();

  // ═══ 初始化加载 ═══
  useEffect(() => {
    store.loadPets();
    store.loadGenConfig();
  }, []);

  // ═══ 创建宠物 ═══
  const createPet = useCallback(async (params: {
    name: string;
    description?: string;
    referenceImagePath?: string;
    basePrompt: string;
    negativePrompt?: string;
  }) => {
    return store.createPet({
      name: params.name,
      description: params.description,
      referenceImagePath: params.referenceImagePath,
      basePrompt: params.basePrompt,
      negativePrompt: params.negativePrompt,
      spriteWidth: DEFAULT_RENDER_CONFIG.spriteWidth,
      spriteHeight: DEFAULT_RENDER_CONFIG.spriteHeight,
      fps: DEFAULT_RENDER_CONFIG.fps,
      scale: DEFAULT_RENDER_CONFIG.scale,
      renderMode: DEFAULT_RENDER_CONFIG.renderMode,
    });
  }, [store]);

  // ═══ 创建内置默认动作（为新宠物） ═══
  const createBuiltinActions = useCallback(async (petId: string, actionNames: string[] = [
    'idle', 'walk', 'happy', 'sleep', 'wave',
  ]) => {
    const { getBuiltinActions } = await import('@/lib/pixelpet/prompts');
    const builtinTemplates = getBuiltinActions();
    const selected = builtinTemplates.filter((a) => actionNames.includes(a.name));

    const actions: PetAction[] = [];
    for (const template of selected) {
      const action = await store.createAction({
        petId,
        name: template.name,
        displayName: template.displayName,
        description: template.description,
        prompt: template.prompt,
        frameCount: template.frameCount,
        frameDuration: template.frameDuration,
        loopMode: template.loopMode,
        triggerEvents: template.triggerEvents,
      });
      actions.push(action);
    }
    return actions;
  }, [store]);

  // ═══ 批量生成动作帧图 ═══
  const generateFrames = useCallback(async (
    action: PetAction,
    onProgress?: (event: GenerationProgressEvent) => void,
  ) => {
    if (!store.activePet) throw new Error('没有活跃的宠物');

    store.setGenerating(true);

    try {
      const results = await generateActionFrames(
        store.genConfig,
        store.activePet.basePrompt,
        action.prompt,
        action.frameCount,
        onProgress,
      );

      // 逐帧更新状态（保存到本地并通过Tauri命令更新数据库）
      for (const result of results) {
        const frame = action.frames[result.frameIndex];
        if (frame) {
          // 将base64图片数据保存为文件，然后更新帧状态
          const { invoke } = await import('@tauri-apps/api/core');
          await invoke('update_frame_status', {
            frameId: frame.id,
            status: 'ready',
            imagePath: result.frameIndex.toString(), // 实际应保存文件后返回路径
          });
        }
      }

      return results.length;
    } finally {
      store.setGenerating(false);
    }
  }, [store.activePet, store.genConfig, store]);

  // ═══ 切换到像素模式并设为活跃宠物 ═══
  const activatePixelPet = useCallback(async (petId: string) => {
    store.setPetMode('pixel');
    await store.setActivePet(petId);
  }, [store]);

  // ═══ 测试生成API连接 ═══
  const testGenApi = useCallback(async (): Promise<{ success: boolean; error?: string }> => {
    try {
      const { invoke } = await import('@tauri-apps/api/core');
      // 用一个简单提示词测试
      const result = await invoke<string>('test_pixel_gen_api', {
        prompt: 'a single pixel art cat standing still, 64x64 pixels',
      });
      return { success: true };
    } catch (e) {
      return { success: false, error: e instanceof Error ? e.message : String(e) };
    }
  }, []);

  return {
    // Store 数据
    pets: store.pets,
    activePet: store.activePet,
    currentPetActions: store.currentPetActions,
    genConfig: store.genConfig,
    petMode: store.petMode,
    isLoading: store.isLoading,
    isGenerating: store.isGenerating,
    generationProgress: store.generationProgress,

    // 操作方法
    loadPets: store.loadPets,
    loadActivePet: store.loadActivePet,
    loadPetActions: store.loadPetActions,
    createPet,
    updatePet: store.updatePet,
    setActivePet: store.setActivePet,
    deletePet: store.deletePet,
    createAction: store.createAction,
    updateAction: store.updateAction,
    deleteAction: store.deleteAction,
    updateGenConfig: store.updateGenConfig,
    setPetMode: store.setPetMode,

    // 组合操作
    createBuiltinActions,
    generateFrames,
    activatePixelPet,
    testGenApi,
  };
}
