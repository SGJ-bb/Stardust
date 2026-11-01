/**
 * 外观设置 —— 主题/布局（由 SettingsPage 承载）之外，这里负责：
 * 气泡皮肤商店（星尘币经济）+ 用户画像（"AI 眼中的你"，对应 Android ProfileActivity）。
 */
import { useCallback, useEffect, useState } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Coins, Check, Sparkles } from "lucide-react";
import { getBubbleSkinList, purchaseBubbleSkin, equipBubbleSkinById, getCoins } from "@/lib/tauri";
import type { PurchaseResult } from "@/types/tauri";
import { usePersonaStore } from "@/stores/usePersonaStore";
import { emit } from "@/lib/events";
import { kvGet } from "@/lib/db";
import type { UserProfileConfig } from "@/types/settings";
import { Select, SectionTitle } from "./common";

type BubbleSkinItem = {
  id: string;
  name: string;
  price: number;
  owned: boolean;
  style: { userBg: string; petBg: string; userText: string; petText: string; radius: number; border?: string };
};

const PROFILE_DEFAULTS: UserProfileConfig = {
  nickname: "",
  gender: "",
  birthday: "",
  appearance: "",
  description: "",
};

/** 把 #AARRGGBB / #RRGGBB 转成 CSS rgb()/rgba()（皮肤目录沿用 Android ARGB 色值格式） */
function toCssColor(c: string): string {
  const m = /^#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})?$/.exec(c.trim());
  if (!m) return c;
  const hexTo255 = (h: string) => parseInt(h, 16);
  const [, a, r, g, b] = m;
  if (b !== undefined) {
    // ARGB：首字节是 alpha
    return `rgba(${hexTo255(r)}, ${hexTo255(g)}, ${hexTo255(b)}, ${(hexTo255(a) / 255).toFixed(2)})`;
  }
  return `rgb(${hexTo255(r)}, ${hexTo255(g)}, ${hexTo255(b)})`;
}

/**
 * 外观设置组件
 */
export function AppearanceSettings() {
  const { settings, updateSettings } = useSettingsStore();

  // ─── 气泡皮肤 ───
  const [skins, setSkins] = useState<BubbleSkinItem[]>([]);
  const [activeId, setActiveId] = useState<string>("default");
  const [coins, setCoins] = useState<number>(settings.coins ?? 0);
  const [shopMsg, setShopMsg] = useState<{ kind: "ok" | "err"; text: string } | null>(null);

  const refresh = useCallback(async () => {
    try {
      const list = (await getBubbleSkinList()) as BubbleSkinItem[];
      setSkins(list);
      // 装备态：引擎 equipBubbleSkin 双写全局 kv + 当前角色 bubbleSkin 字段，两处都读以保准确
      const store = usePersonaStore.getState();
      const personaSkin = (store.activePersona ?? store.personas[0])?.bubbleSkin;
      setActiveId((await kvGet<string>("bubble_skin_active")) || personaSkin || "default");
      setCoins(await getCoins());
    } catch (e) {
      console.error("加载气泡皮肤失败:", e);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh, settings.coins]);

  const handlePurchase = async (id: string, name: string) => {
    const r: PurchaseResult = await purchaseBubbleSkin(id);
    if (r.success) {
      setShopMsg({ kind: "ok", text: `已购买「${name}」` });
    } else {
      setShopMsg({ kind: "err", text: r.error ?? "购买失败" });
    }
    await refresh();
  };
  const handleEquip = async (id: string) => {
    const ok = await equipBubbleSkinById(id);
    if (ok) {
      emit("data-changed", { scope: "bubbleSkin" });
      setActiveId(id);
      setShopMsg({ kind: "ok", text: "已装备，聊天界面即时生效" });
    } else {
      setShopMsg({ kind: "err", text: "装备失败（可能未购买）" });
    }
    await refresh();
  };

  // ─── 用户画像 ───
  const profile = settings.userProfile ?? PROFILE_DEFAULTS;
  const patchProfile = (partial: Partial<UserProfileConfig>) => {
    updateSettings({ userProfile: { ...PROFILE_DEFAULTS, ...profile, ...partial } });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">外观与个性化</h2>

      {/* ═══════ 气泡皮肤商店 ═══════ */}
      <SectionTitle title="聊天气泡皮肤" desc="用星尘币兑换气泡配色，装备后全局聊天生效" />
      <Card>
        <CardContent className="p-4 space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-xs text-[var(--color-muted-foreground)]">
              购买与装备记录会同步到角色档案；余额随消费自动更新。
            </p>
            <span className="flex items-center gap-1.5 text-sm font-semibold text-[var(--color-primary)] px-3 py-1 rounded-full bg-[var(--color-primary)]/10">
              <Coins className="h-4 w-4" /> {coins} 星尘币
            </span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            {skins.map((s) => {
              const equipped = activeId === s.id;
              return (
                <div
                  key={s.id}
                  className={`rounded-xl border p-3 space-y-2 transition-all ${
                    equipped
                      ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                      : "border-[var(--color-border)]"
                  }`}
                >
                  {/* 预览：模拟左右两条气泡 */}
                  <div className="space-y-1.5 rounded-lg bg-[var(--color-muted)]/20 p-2">
                    <div
                      className="ml-auto max-w-[80%] px-2.5 py-1 text-[10px]"
                      style={{
                        background: toCssColor(s.style.userBg),
                        color: toCssColor(s.style.userText),
                        borderRadius: s.style.radius / 2,
                        border: s.style.border,
                      }}
                    >
                      我说的话
                    </div>
                    <div
                      className="max-w-[80%] px-2.5 py-1 text-[10px]"
                      style={{
                        background: toCssColor(s.style.petBg),
                        color: toCssColor(s.style.petText),
                        borderRadius: s.style.radius / 2,
                        border: s.style.border,
                      }}
                    >
                      星尘的回复～
                    </div>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium">{s.name}</span>
                    <span className="text-[10px] text-[var(--color-muted-foreground)] flex items-center gap-0.5">
                      {s.price > 0 ? (<><Coins className="h-3 w-3" />{s.price}</>) : "免费"}
                    </span>
                  </div>
                  {equipped ? (
                    <Button size="sm" variant="ghost" className="w-full text-[var(--color-primary)]" disabled>
                      <Check className="h-3.5 w-3.5 mr-1" /> 使用中
                    </Button>
                  ) : s.owned ? (
                    <Button size="sm" variant="outline" className="w-full" onClick={() => void handleEquip(s.id)}>
                      装备
                    </Button>
                  ) : (
                    <Button size="sm" className="w-full btn-primary" onClick={() => void handlePurchase(s.id, s.name)}>
                      <Sparkles className="h-3.5 w-3.5 mr-1" /> {coins >= s.price ? "购买并装备" : "星尘币不足"}
                    </Button>
                  )}
                </div>
              );
            })}
          </div>
          {shopMsg && (
            <div className={`text-xs px-3 py-2 rounded-md ${shopMsg.kind === "ok" ? "bg-green-500/10 text-green-500" : "bg-red-500/10 text-red-400"}`}>
              {shopMsg.text}
            </div>
          )}
        </CardContent>
      </Card>

      {/* ═══════ 用户画像 ═══════ */}
      <SectionTitle
        title="用户画像 · AI 眼中的你"
        desc="告诉星尘你是谁（对应安卓版「我的资料」页），这些信息会注入系统提示词，让 AI 更懂你"
      />
      <Card>
        <CardContent className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-label block mb-1.5">昵称 / 称呼</label>
              <Input
                value={profile.nickname ?? ""}
                onChange={(e) => patchProfile({ nickname: e.target.value })}
                placeholder="AI 平时怎么叫你"
                className="input-field"
              />
            </div>
            <div>
              <label className="text-label block mb-1.5">性别</label>
              <Select
                value={profile.gender ?? ""}
                onChange={(v) => patchProfile({ gender: v as UserProfileConfig["gender"] })}
                options={[
                  { value: "", label: "不愿透露" },
                  { value: "male", label: "男" },
                  { value: "female", label: "女" },
                  { value: "other", label: "其他" },
                ]}
              />
            </div>
          </div>
          <div>
            <label className="text-label block mb-1.5">生日</label>
            <Input
              type="date"
              value={profile.birthday ?? ""}
              onChange={(e) => patchProfile({ birthday: e.target.value })}
              className="input-field"
            />
            <p className="text-[10px] text-[var(--color-muted-foreground)] mt-1 opacity-60">用于生日提醒与星座话题（留空则不告知 AI）。</p>
          </div>
          <div>
            <label className="text-label block mb-1.5">外貌描述</label>
            <Input
              value={profile.appearance ?? ""}
              onChange={(e) => patchProfile({ appearance: e.target.value })}
              placeholder="例如：短发、戴黑框眼镜、总穿卫衣"
              className="input-field"
            />
          </div>
          <Separator />
          <div>
            <label className="text-label block mb-1.5">自我介绍 / 补充设定</label>
            <Textarea
              value={profile.description ?? ""}
              onChange={(e) => patchProfile({ description: e.target.value })}
              placeholder="职业、性格、喜好、和 AI 的关系设定……写得越具体，AI 越贴近你心中的样子"
              className="min-h-[100px]"
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
