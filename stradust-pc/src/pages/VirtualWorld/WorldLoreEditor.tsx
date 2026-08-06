import { useState } from "react";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";
import { useVirtualWorldStore } from "@/stores/useVirtualWorldStore";
import { Save } from "lucide-react";

interface WorldLoreEditorProps {
  worldId: string;
  initialLore: string;
}

/**
 * 世界观编辑器组件
 */
export function WorldLoreEditor({ worldId, initialLore }: WorldLoreEditorProps) {
  const { updateWorldLore } = useVirtualWorldStore();
  const [lore, setLore] = useState(initialLore);

  const handleSave = () => {
    updateWorldLore(worldId, lore);
  };

  return (
    <div className="space-y-4">
      <Textarea
        value={lore}
        onChange={(e) => setLore(e.target.value)}
        placeholder="编写世界观设定..."
        rows={15}
        className="font-mono text-sm"
      />
      <Button onClick={handleSave}>
        <Save className="h-4 w-4 mr-2" />
        保存世界观
      </Button>
    </div>
  );
}
