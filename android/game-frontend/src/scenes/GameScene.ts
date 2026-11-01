import Phaser from 'phaser';
import { makeCharacterSheet, playerDef } from '../characters';
import { buildTownMap, buildHouse } from '../tileset';
import { bridgeOn } from '../bridge';
import { preloadPixelAsset, keyImageToTransparent } from '../pixelAssets';

// AI 预烘焙资产(路线A): 用户零 API, 观感接近星露谷。路径为 public/ 下的原图。
const EMILY_SHEET_URL = 'assets/emily_sheet.jpg';
const EMILY_TEX = 'emily_ai';

export class GameScene extends Phaser.Scene {
  private player!: Phaser.Physics.Arcade.Sprite;
  private cursors!: { left: Phaser.Input.Keyboard.Key; right: Phaser.Input.Keyboard.Key; up: Phaser.Input.Keyboard.Key; down: Phaser.Input.Keyboard.Key };
  private moveText!: Phaser.GameObjects.Text;
  private emily!: Phaser.GameObjects.Sprite;
  private emilyTarget = { x: 720, y: 620, flip: false, moving: false };
  private emilyBobPhase = 0;
  private readonly speed = 180;
  private readonly vpad = { left: false, right: false, up: false, down: false };
  private readonly dpadSize = 96;

  constructor() {
    super('Game');
  }

  preload(): void {
    preloadPixelAsset(this, EMILY_SHEET_URL);
  }

  create(): void {
    this.physics.world.setBounds(0, 0, 1280, 1280);

    // 按需把 AI 原图抠白成透明纹理(幂等: 已在场景级生成过一次则跳过)
    keyImageToTransparent(this, EMILY_SHEET_URL, EMILY_TEX);

    // 地面: 正宗 tilemap 多地貌(草地/湖/石板路/农田/石/花/树)
    buildTownMap(this);

    // 关卡角色: 玩家仍用程序化行走 sheet(player_w_d/_s 动画), 高像素形象兜底
    makeCharacterSheet(this, 'player', playerDef);
    this.player = this.physics.add.sprite(640, 640, 'player_d0').setCollideWorldBounds(true);
    this.player.setScale(2);

    // 示例 NPC(M1 闭环 + 路线A美术): 艾米丽用 AI 预烘焙透明纹理, 位置/朝向由 Kotlin 桥下发驱动
    this.emily = this.add.sprite(720, 620, EMILY_TEX).setScale(2.4);
    this.setupEmilyBridge();

    this.cameras.main.setBounds(0, 0, 1280, 1280);
    this.cameras.main.startFollow(this.player, true, 0.1, 0.1);
    this.cameras.main.setZoom(2.0);

    this.cursors = {
      left: this.input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.LEFT),
      right: this.input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.RIGHT),
      up: this.input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.UP),
      down: this.input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.DOWN)
    };

    // HUD
    this.moveText = this.add.text(12, 12, '方向键移动', {
      fontFamily: 'monospace',
      fontSize: '14px',
      color: '#ffffff'
    }).setScrollFactor(0).setDepth(10);

    // 真机触屏: 左下角虚拟方向键
    this.setupVirtualDpad();

    // 小镇建筑: 玩家小木屋
    buildHouse(this, 1160, 190);
  }

  /** M1 闭环: 前端只渲染跟随, 目标位置/朝向由 Kotlin 桥下发(确定性, 零 LLM). */
  private setupEmilyBridge(): void {
    bridgeOn('npc_move', (payload) => {
      const o = payload as { id?: string; x?: number; y?: number; flip?: boolean; moving?: boolean };
      if (!o || o.id !== 'emily') return;
      this.emilyTarget.x = o.x ?? this.emilyTarget.x;
      this.emilyTarget.y = o.y ?? this.emilyTarget.y;
      this.emilyTarget.flip = !!o.flip;
      this.emilyTarget.moving = !!o.moving;
    });
  }

  private syncEmily(): void {
    const t = this.emilyTarget;
    this.emily.setFlipX(t.flip);
    const dx = t.x - this.emily.x;
    const dy = t.y - this.emily.y;
    const d = Math.hypot(dx, dy);
    if (t.moving && d > 1) {
      const k = Math.min(0.35, 2 / d); // 每帧插值比例, 防跳动
      this.emily.x += dx * k;
      this.emily.y += dy * k;
    } else {
      this.emily.x = t.x;
      this.emily.y = t.y;
    }
    // AI 静态帧没有行走动画, 用轻微上下浮动制造"活着"的呼吸感
    this.emilyBobPhase += t.moving ? 0.4 : 0.12;
    const bob = Math.sin(this.emilyBobPhase) * (t.moving ? 4 : 2);
    this.emily.y += bob;
  }

  update(time: number, _delta: number): void {
    if (!this.moveText) this.moveText = this.textExists();
    const c = this.cursors;
    const v = this.vpad;
    let vx = 0;
    let vy = 0;
    if (c.left.isDown || v.left) vx = -1;
    if (c.right.isDown || v.right) vx = 1;
    if (c.up.isDown || v.up) vy = -1;
    if (c.down.isDown || v.down) vy = 1;
    if (vx !== 0 || vy !== 0) {
      // 归一化对角线
      const len = Math.hypot(vx, vy);
      this.player.setVelocity((vx / len) * this.speed, (vy / len) * this.speed);
      // 动画与朝向: 左右用侧向(镜像), 上下用正面
      if (vx !== 0) {
        this.player.setFlipX(vx < 0);
        if (this.player.anims.currentAnim?.key !== 'player_w_s') this.player.play('player_w_s');
      } else if (vy !== 0) {
        this.player.setFlipX(false);
        if (this.player.anims.currentAnim?.key !== 'player_w_d') this.player.play('player_w_d');
      }
    } else {
      this.player.setVelocity(0, 0);
      this.player.anims.stop();
    }
    this.syncEmily();
    if (time % 400 < _delta) {
      this.moveText.setText(`pos: ${Math.round(this.player.x)}, ${Math.round(this.player.y)}`);
    }
  }

  /** 触屏虚拟方向键: 左下角四向按钮, 按下置位, 松手复位。 */
  private setupVirtualDpad(): void {
    const s = this.dpadSize;
    const cx = 40 + s;               // 左顶点组中心
    const cy = this.scale.height - 40 - s;
    const offset = s * 0.72;

    // 半透明示意图形(HUD 层, 不阻挡交互)
    const g = this.add.graphics().setScrollFactor(0).setDepth(19);
    g.fillStyle(0xffffff, 0.16);
    [[0, -1], [0, 1], [-1, 0], [1, 0]].forEach(([dx, dy]) => {
      g.fillCircle(cx + dx * offset, cy + dy * offset, s / 2 - 6);
    });

    // 可点按交互区
    const bind = (dir: 'left' | 'right' | 'up' | 'down', x: number, y: number): void => {
      const zone = this.add.zone(x, y, s, s).setScrollFactor(0).setInteractive();
      zone.setDepth(20);
      zone.on('pointerdown', () => { this.vpad[dir] = true; });
    };
    bind('up', cx, cy - offset);
    bind('down', cx, cy + offset);
    bind('left', cx - offset, cy);
    bind('right', cx + offset, cy);

    // 抬手复位所有方向(避免卡键)
    this.input.on('pointerup', () => {
      this.vpad.left = this.vpad.right = this.vpad.up = this.vpad.down = false;
    });
  }

  private textExists(): Phaser.GameObjects.Text {
    return this.add.text(12, 12, '', { fontSize: '14px' }).setColor('#ffffff').setScrollFactor(0).setDepth(10);
  }
}