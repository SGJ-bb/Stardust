/**
 * 昵称管理页 —— 对应 Android NicknameManager（AI 为主人起的称呼）
 */

import { useEffect, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Sparkles, Trash2, Check, Tag } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PageContainer } from "@/components/layout/PageContainer";
import { nicknamesFacade } from "@/lib/tauri";
import { usePersonaStore } from "@/stores/usePersonaStore";
import type { NicknameRecord } from "@/engine/kinds";

export function NicknamePage() {
  const personas = usePersonaStore((s) => s.personas);
  const [personaId, setPersonaId] = useState(personas[0]?.id ?? "");
  const [list, setList] = useState<NicknameRecord[]>([]);
  const [input, setInput] = useState("");

  const reload = () => {
    if (!personaId) return;
    nicknamesFacade.list(personaId).then((l) => setList(l as NicknameRecord[]));
  };

  useEffect(reload, [personaId]);

  const handleAdd = async () => {
    const name = input.trim();
    if (!name || !personaId) return;
    await nicknamesFacade.set(personaId, name);
    setInput("");
    reload();
  };

  const handleSetActive = async (record: NicknameRecord) => {
    await nicknamesFacade.set(personaId, record.nickname);
    reload();
  };

  const handleDelete = async (id: string) => {
    await nicknamesFacade.remove(id);
    reload();
  };

  return (
    <PageContainer title="昵称管理" description="AI 对你的称呼，来自聊天中的自动总结或手动添加">
      <div className="flex flex-wrap gap-2 mb-4">
        {personas.map((p) => (
          <Button key={p.id} size="sm" variant={personaId === p.id ? "default" : "outline"} onClick={() => setPersonaId(p.id)}>
            {p.name}
          </Button>
        ))}
      </div>

      <div className="flex gap-2 mb-4 max-w-md">
        <Input value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => e.key === "Enter" && handleAdd()} placeholder="添加一个新称呼…" />
        <Button onClick={handleAdd}><Sparkles className="h-4 w-4 mr-1" />添加</Button>
      </div>

      <AnimatePresence>
        {list.length === 0 ? (
          <div className="text-center py-14 text-[var(--color-muted-foreground)]">
            <Tag className="h-10 w-10 mx-auto mb-3 opacity-40" />
            <p className="text-sm">还没有昵称。聊天中 AI 会主动为你起称呼，也可以手动添加</p>
          </div>
        ) : (
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {list.map((n) => (
              <motion.div
                key={n.id}
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0 }}
                className={`surface-card p-3 flex items-center gap-2 ${n.active ? "ring-1 ring-[var(--color-primary)]" : ""}`}
              >
                <span className="text-base font-medium">{n.nickname}</span>
                <span className="text-[10px] text-[var(--color-muted-foreground)]">{n.source === "llm" ? "AI起的" : "手动"}</span>
                <div className="ml-auto flex gap-1">
                  {!n.active && (
                    <Button size="sm" variant="ghost" title="设为当前称呼" onClick={() => handleSetActive(n)}>
                      <Check className="h-3.5 w-3.5" />
                    </Button>
                  )}
                  <Button size="sm" variant="ghost" className="text-red-400" onClick={() => handleDelete(n.id)}>
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </motion.div>
            ))}
          </div>
        )}
      </AnimatePresence>
    </PageContainer>
  );
}
