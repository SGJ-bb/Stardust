import Phaser from 'phaser';
import { BootScene } from './scenes/BootScene';
import { GameScene } from './scenes/GameScene';

const config: Phaser.Types.Core.GameConfig = {
  type: Phaser.AUTO,
  parent: 'game',
  backgroundColor: '#2b1f3d',
  scale: {
    mode: Phaser.Scale.RESIZE,
    autoCenter: Phaser.Scale.CENTER_BOTH
  },
  physics: {
    default: 'arcade',
    arcade: { gravity: { x: 0, y: 0 }, debug: false }
  },
  scene: [BootScene, GameScene],
  // 低端机友好
  powerPreference: 'high-performance',
  fps: { min: 20, target: 60, smoothStep: true }
};

// eslint-disable-next-line no-new
new Phaser.Game(config);