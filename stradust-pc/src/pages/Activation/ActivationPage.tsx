import { useNavigate } from "react-router";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowLeft, Key, CheckCircle, XCircle } from "lucide-react";
import { useState } from "react";
import { activateCode } from "@/lib/tauri";

/**
 * 激活码页面
 * 对应Android ActivationActivity
 */
export function ActivationPage() {
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);
  const [isActivating, setIsActivating] = useState(false);

  const handleActivate = async () => {
    if (!code.trim()) return;
    setIsActivating(true);
    setResult(null);
    try {
      // 调用后端验证激活码
      const res = await activateCode(code.trim());
      setResult({
        success: res.success,
        message: res.success ? "激活成功！已解锁高级功能" : (res.error ?? "激活码无效，请检查后重试"),
      });
    } catch (error) {
      setResult({
        success: false,
        message: "激活码验证失败，请检查网络连接后重试",
      });
    } finally {
      setIsActivating(false);
    }
  };

  return (
    <PageContainer>
      <div className="flex items-center gap-3 mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          返回
        </Button>
        <h1 className="text-2xl font-bold text-[var(--color-card-foreground)]">激活码</h1>
      </div>

      <Card className="max-w-md mx-auto">
        <CardContent className="p-6 text-center space-y-4">
          <Key className="h-12 w-12 mx-auto text-[var(--color-primary)]" />
          <h2 className="text-lg font-semibold text-[var(--color-card-foreground)]">输入激活码</h2>
          <p className="text-sm text-[var(--color-muted-foreground)]">输入激活码解锁高级功能</p>

          <Input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="请输入激活码"
            className="text-center"
          />

          <Button onClick={handleActivate} disabled={!code.trim() || isActivating} className="w-full">
            {isActivating ? "验证中..." : "激活"}
          </Button>

          {result && (
            <div className={`flex items-center justify-center gap-2 p-3 rounded-[var(--app-radius)] ${
              result.success ? "bg-green-50 text-green-700" : "bg-red-50 text-red-700"
            }`}>
              {result.success ? <CheckCircle className="h-5 w-5" /> : <XCircle className="h-5 w-5" />}
              <span className="text-sm">{result.message}</span>
            </div>
          )}
        </CardContent>
      </Card>
    </PageContainer>
  );
}
