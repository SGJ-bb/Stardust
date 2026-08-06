import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { useMomentsStore } from "@/stores/useMomentsStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  ArrowLeft,
  Plus,
  Heart,
  MessageCircle,
  Image as ImageIcon,
  Send,
  RefreshCw,
  Sparkles,
} from "lucide-react";
import { formatTime } from "@/lib/utils";
import { useState } from "react";
import {
  commentMoment as commentMomentApi,
  publishMoment as publishMomentApi,
} from "@/lib/tauri";

/**
 * 朋友圈页面
 * 对应Android MomentsActivity
 */
export function MomentsPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();
  const { moments, publishMoment, likeMoment, commentMoment } = useMomentsStore();
  const [showPublishDialog, setShowPublishDialog] = useState(false);
  const [content, setContent] = useState("");
  /** 评论相关状态 */
  const [commentingMomentId, setCommentingMomentId] = useState<string | null>(null);
  const [commentContent, setCommentContent] = useState("");
  /** 刷新动画 */
  const [isRefreshing, setIsRefreshing] = useState(false);

  const handlePublish = () => {
    if (!personaId || !content.trim()) return;
    publishMoment(personaId, content.trim(), []);
    // 调用后端API
    publishMomentApi(personaId, content.trim(), []).catch((error) => {
      console.error("发布朋友圈失败:", error);
    });
    setContent("");
    setShowPublishDialog(false);
  };

  const handleComment = (momentId: string) => {
    if (!commentContent.trim()) return;
    commentMoment(momentId, "user", "我", commentContent.trim());
    // 调用后端API
    commentMomentApi(momentId, commentContent.trim()).catch((error) => {
      console.error("评论失败:", error);
    });
    setCommentContent("");
    setCommentingMomentId(null);
  };

  const handleRefresh = () => {
    setIsRefreshing(true);
    setTimeout(() => setIsRefreshing(false), 800);
  };

  return (
    <PageContainer>
      <div className="page-content">
        {/* ====== 顶部区域：标题 + 刷新 ====== */}
        <div className="flex items-center justify-between mb-6 sticky top-0 z-10 bg-[var(--color-background)]/80 backdrop-blur-lg py-2 -mt-2">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="text-[var(--color-muted-foreground)] hover:text-[var(--color-primary)]">
              <ArrowLeft className="h-5 w-5" />
            </Button>
            <h1 className="text-2xl font-bold bg-[var(--theme-gradient)] bg-clip-text text-transparent flex items-center gap-2">
              <Sparkles className="h-6 w-6 text-[var(--color-primary)]" />
              动态
            </h1>
          </div>
          {/* 刷新按钮 */}
          <button
            onClick={handleRefresh}
            className="p-2 rounded-xl text-[var(--color-muted-foreground)] hover:text-[var(--color-primary)] hover:bg-[var(--color-muted)]/30 transition-all duration-300"
          >
            <RefreshCw className={`h-4.5 w-4.5 ${isRefreshing ? "animate-spin" : ""}`} />
          </button>
        </div>

        {/* ====== 动态流式布局（朋友圈风格）===== */}
        <ScrollArea className="flex-1 pb-24 pr-1">
          {moments.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-24">
              <ImageIcon className="h-16 w-16 text-[var(--color-muted-foreground)]/35 mb-4 animate-pulse" />
              <p className="text-sm text-[var(--color-muted-foreground)]">还没有动态</p>
              <p className="text-xs text-[var(--color-muted-foreground)]/50 mt-1">点击右下角发布第一条</p>
            </div>
          ) : (
            <div className="space-y-5 max-w-xl mx-auto">
              {moments.map((moment) => (
                <article
                  key={moment.id}
                  className="glass-card rounded-2xl overflow-hidden border border-[var(--color-border)]/20 transition-all duration-300 hover:border-[var(--color-border)]/40 hover:shadow-lg hover:shadow-black/[0.03]"
                >
                  {/* 卡片头部：头像 + 名字 + 时间 */}
                  <div className="p-5 pb-3">
                    <div className="flex items-center gap-3 mb-4">
                      {/* 用户头像 */}
                      <Avatar className="h-10 w-10 ring-2 ring-[var(--color-primary)]/20 ring-offset-2 ring-offset-transparent">
                        <AvatarFallback className="text-xs bg-gradient-to-br from-[var(--color-primary)] to-[var(--color-primary)]/70 text-white font-medium">
                          AI
                        </AvatarFallback>
                      </Avatar>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-[var(--color-card-foreground)] truncate">AI 助手</p>
                        <span className="text-[11px] text-[var(--color-muted-foreground)]">{formatTime(moment.createdAt)}</span>
                      </div>
                    </div>

                    {/* 文字内容 */}
                    <p className="text-sm text-[var(--color-card-foreground)]/90 leading-relaxed whitespace-pre-wrap break-words">
                      {moment.content}
                    </p>

                    {/* 图片网格 */}
                    {moment.images.length > 0 && (
                      <div
                        className={`grid gap-1.5 mt-3.5 ${
                          moment.images.length === 1 ? "grid-cols-1 max-w-[240px]" :
                          moment.images.length === 2 || moment.images.length === 4 ? "grid-cols-2" :
                          "grid-cols-3"
                        }`}
                      >
                        {moment.images.map((img, i) => (
                          <div
                            key={i}
                            className={`bg-[var(--color-muted)]/60 overflow-hidden ${
                              moment.images.length === 1 ? "rounded-[var(--radius-md)] aspect-[4/3]" :
                              "rounded-[var(--radius-md)] aspect-square"
                            } relative group/img cursor-pointer`}
                          >
                            {/* 图片占位 - 实际项目中替换为真实图片 */}
                            <div className="absolute inset-0 bg-gradient-to-br from-[var(--color-muted)] to-[var(--color-muted)]/50 flex items-center justify-center group-hover/img:scale-105 transition-transform duration-500">
                              <ImageIcon className="h-8 w-8 text-[var(--color-muted-foreground)]/30" />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* 互动区：点赞 / 评论 */}
                  <div className="px-5 pb-4 pt-2 border-t border-[var(--color-border)]/10">
                    <div className="flex items-center gap-5">
                      {/* 点赞按钮 */}
                      <button
                        onClick={() => likeMoment(moment.id)}
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-300 ${
                          moment.liked
                            ? "text-red-400 bg-red-500/10"
                            : "text-[var(--color-muted-foreground)] bg-[var(--color-muted)]/20 hover:bg-red-500/10 hover:text-red-400"
                        }`}
                      >
                        <Heart
                          className={`h-3.5 w-3.5 transition-all ${
                            moment.liked ? "fill-red-400 scale-110" : ""
                          }`}
                        />
                        {moment.likes > 0 && <span>{moment.likes}</span>}
                        {!moment.likes && <span>点赞</span>}
                      </button>

                      {/* 评论按钮 */}
                      <button
                        onClick={() =>
                          setCommentingMomentId(
                            commentingMomentId === moment.id ? null : moment.id
                          )
                        }
                        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-300 ${
                          commentingMomentId === moment.id
                            ? "text-[var(--color-primary)] bg-[var(--color-primary)]/10"
                            : "text-[var(--color-muted-foreground)] bg-[var(--color-muted)]/20 hover:bg-[var(--color-primary)]/10 hover:text-[var(--color-primary)]"
                        }`}
                      >
                        <MessageCircle className="h-3.5 w-3.5" />
                        {moment.comments.length > 0 && <span>{moment.comments.length}</span>}
                        {!moment.comments.length && <span>评论</span>}
                      </button>
                    </div>

                    {/* 评论列表 */}
                    {moment.comments.length > 0 && (
                      <div className="mt-3 space-y-2 rounded-xl bg-[var(--color-muted)]/15 p-3 border border-[var(--color-border)]/5">
                        {moment.comments.map((comment) => (
                          <p key={comment.id} className="text-xs leading-relaxed">
                            <span className="font-semibold text-[var(--color-primary)] mr-1.5">
                              {comment.authorName}
                            </span>
                            {comment.replyTo && (
                              <span className="text-[var(--color-muted-foreground)] mx-0.5">
                                回复{" "}
                                <span className="font-medium text-[var(--color-card-foreground)]/70">
                                  {comment.replyTo}
                                </span>{" "}
                                :
                              </span>
                            )}
                            <span className="text-[var(--color-card-foreground)]/80">
                              {comment.content}
                            </span>
                          </p>
                        ))}
                      </div>
                    )}

                    {/* 评论输入框 */}
                    {commentingMomentId === moment.id && (
                      <div className="mt-3 flex gap-2 items-end">
                        <div className="flex-1 relative">
                          <input
                            value={commentContent}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setCommentContent(e.target.value)}
                            placeholder="写下你的想法..."
                            className="h-9 text-xs rounded-xl border-[color-mix(in_srgb,var(--color-border),transparent_30%)] bg-[color-mix(in_srgb,var(--color-muted),transparent_20%)] focus:border-[var(--color-primary)] placeholder:text-[color-mix(in_srgb,var(--color-muted-foreground),transparent_50%)] px-3 outline-none"
                            autoFocus
                            onKeyDown={(e: React.KeyboardEvent<HTMLInputElement>) => {
                              if (e.key === "Enter" && !e.shiftKey) {
                                e.preventDefault();
                                handleComment(moment.id);
                              }
                            }}
                          />
                        </div>
                        <Button
                          size="sm"
                          className="h-9 px-3 rounded-xl bg-[var(--color-primary)] hover:bg-[var(--color-primary)]/90 text-white shrink-0 disabled:opacity-40"
                          onClick={() => handleComment(moment.id)}
                          disabled={!commentContent.trim()}
                        >
                          <Send className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </ScrollArea>

        {/* ====== 固定右下角发布按钮 ====== */}
        <button
          onClick={() => setShowPublishDialog(true)}
          className="fixed right-6 bottom-8 z-50 w-14 h-14 rounded-full bg-[var(--color-primary)] text-white shadow-xl shadow-[var(--color-primary)]/30 flex items-center justify-center hover:scale-110 active:scale-95 transition-all duration-300 group"
          aria-label="发布新动态"
        >
          <Plus className="h-6 w-6 group-hover:rotate-90 transition-transform duration-300" />
          {/* 光晕效果 */}
          <span className="absolute inset-0 rounded-full bg-[var(--color-primary)] opacity-0 group-hover:opacity-20 blur-xl scale-150 transition-opacity duration-300" />
        </button>

        {/* 发布对话框 */}
        <Dialog open={showPublishDialog} onOpenChange={setShowPublishDialog}>
          <DialogContent className="sm:max-w-lg glass-panel border-[var(--color-border)]/30 bg-[var(--color-card)]/95 backdrop-blur-xl">
            <DialogHeader>
              <DialogTitle className="text-[var(--color-card-foreground)] flex items-center gap-2">
                <Sparkles className="h-5 w-5 text-[var(--color-primary)]" />
                发布动态
              </DialogTitle>
            </DialogHeader>
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="分享此刻的想法..."
              rows={4}
              className="rounded-xl border-[var(--color-border)]/50 bg-[var(--color-muted)]/20 focus:border-[var(--color-primary)] resize-none leading-relaxed placeholder:text-[var(--color-muted-foreground)]/50"
            />
            <DialogFooter>
              <Button
                variant="outline"
                onClick={() => setShowPublishDialog(false)}
                className="rounded-xl border-[var(--color-border)]/50"
              >
                取消
              </Button>
              <Button
                onClick={handlePublish}
                disabled={!content.trim()}
                className="rounded-xl bg-[var(--color-primary)] hover:bg-[var(--color-primary)]/90 text-white shadow-md shadow-[var(--color-primary)]/20"
              >
                <Send className="h-4 w-4 mr-2" />
                发布
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </PageContainer>
  );
}
