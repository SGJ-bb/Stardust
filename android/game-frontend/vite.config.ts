import { defineConfig } from 'vite';
import { viteSingleFile } from 'vite-plugin-singlefile';

// 目标:在 Android WebView 里以 file:///android_asset/game/index.html 打开。
// 用 vite-plugin-singlefile 把所有 JS/CSS 内联进单个 index.html，避免 file:// 下 ES module 外链加载受限导致白屏。
export default defineConfig({
  root: '.',
  base: './',
  plugins: [viteSingleFile({ useRecommendedBuildConfig: true })],
  build: {
    target: 'es2020',
    outDir: 'dist',
    emptyOutDir: true
  }
});