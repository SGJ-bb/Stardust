import Phaser from 'phaser';
import { notifyGameReady, bridgeOn } from '../bridge';

/** 展示型场景:确认 Phaser 已跑起来,显示 FPS,并完成桥的就绪握手。 */
export class BootScene extends Phaser.Scene {
  private fpsText!: Phaser.GameObjects.Text;
  private helloText!: Phaser.GameObjects.Text;

  constructor() {
    super('Boot');
  }

  create(): void {
    this.cameras.main.setBackgroundColor('#2b1f3d');

    this.fpsText = this.add.text(12, 12, 'FPS: --', {
      fontFamily: 'monospace',
      fontSize: '16px',
      color: '#cfe8ff'
    }).setScrollFactor(0);

    this.helloText = this.add.text(this.scale.width / 2, this.scale.height / 2, '像素小镇 M0 骨架', {
      fontFamily: 'sans-serif',
      fontSize: '26px',
      color: '#ffffff'
    }).setOrigin(0.5);

    // JS → Kotlin: 告知就绪
    notifyGameReady();

    // Kotlin → JS: 收到 hello,把文案回显到界面(验证双向桥)
    bridgeOn('hello', (payload) => {
      const p = (payload as { text?: string }) || {};
      this.helloText.setText(`与 Kotlin 双向桥已打通 · ${p.text ?? ''}`).setColor('#8dffa6');
    });

    // 演示 K→J:此时 Kotlin 应已用 evaluateJavascript 下发过 hello;但为了演示两路,附一个点击
    this.input.on('pointerdown', () => {
      this.scene.start('Game');
    });

    this.add.text(this.scale.width / 2, this.scale.height - 40, '点击屏幕进入小镇', {
      fontFamily: 'sans-serif',
      fontSize: '18px',
      color: '#ffd166'
    }).setOrigin(0.5).setScrollFactor(0);
  }

  update(): void {
    this.fpsText.setText(`FPS: ${Math.round(this.game.loop.actualFps)}`);
  }
}