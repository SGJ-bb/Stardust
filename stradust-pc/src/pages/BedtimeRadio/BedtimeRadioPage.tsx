import { useParams, useNavigate } from "react-router";
import { useChatStore } from "@/stores/useChatStore";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Slider } from "@/components/ui/slider";
import {
  ArrowLeft,
  Radio,
  Play,
  Pause,
  SkipForward,
  SkipBack,
  Volume2,
  VolumeX,
  Music,
} from "lucide-react";
import { useState, useEffect, useRef, useCallback } from "react";
import { getRadioList, playRadio } from "@/lib/tauri";

/** 电台数据类型 */
interface RadioItem {
  id: string;
  title: string;
  url: string;
  cover: string;
}

/**
 * 星尘电台页面
 * 对应Android BedtimeRadioActivity
 */
export function BedtimeRadioPage() {
  const { personaId: urlPersonaId } = useParams<{ personaId: string }>();
  const { currentPersonaId } = useChatStore();
  const personaId = urlPersonaId ?? currentPersonaId ?? "";
  const navigate = useNavigate();

  const [radioList, setRadioList] = useState<RadioItem[]>([]);
  const [playing, setPlaying] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [volume, setVolume] = useState(0.7);
  const [isMuted, setIsMuted] = useState(false);
  const [progress, setProgress] = useState(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);

  /** 从后端加载电台列表 */
  useEffect(() => {
    getRadioList()
      .then((list) => setRadioList(list ?? []))
      .catch((error) => console.error("加载电台列表失败:", error))
      .finally(() => setIsLoading(false));
  }, []);

  /** 进度更新 */
  useEffect(() => {
    if (!audioRef.current || !playing) return;

    const updateProgress = () => {
      if (audioRef.current && audioRef.current.duration) {
        setProgress((audioRef.current.currentTime / audioRef.current.duration) * 100);
      }
    };

    const interval = setInterval(updateProgress, 500);
    return () => clearInterval(interval);
  }, [playing]);

  /** 播放/暂停电台 */
  const togglePlay = useCallback(
    async (id: string) => {
      if (playing === id) {
        // 暂停当前播放
        audioRef.current?.pause();
        setPlaying(null);
        setProgress(0);
      } else {
        // 调用后端播放
        try {
          await playRadio(id);
        } catch (error) {
          console.error("播放电台失败:", error);
        }
        // 同时使用前端Audio播放音频
        const radio = radioList.find((r) => r.id === id);
        if (radio?.url) {
          if (!audioRef.current) {
            audioRef.current = new Audio();
            audioRef.current.addEventListener("ended", () => {
              setPlaying(null);
              setProgress(0);
            });
          }
          audioRef.current.src = radio.url;
          audioRef.current.volume = isMuted ? 0 : volume;
          audioRef.current.play().catch((error) => {
            console.error("音频播放失败:", error);
          });
        }
        setPlaying(id);
        setProgress(0);
      }
    },
    [playing, radioList, volume, isMuted]
  );

  /** 上一首 */
  const playPrev = () => {
    if (!playing || radioList.length === 0) return;
    const currentIndex = radioList.findIndex((r) => r.id === playing);
    const prevIndex = currentIndex <= 0 ? radioList.length - 1 : currentIndex - 1;
    togglePlay(radioList[prevIndex].id);
  };

  /** 下一首 */
  const playNext = () => {
    if (!playing || radioList.length === 0) return;
    const currentIndex = radioList.findIndex((r) => r.id === playing);
    const nextIndex = currentIndex >= radioList.length - 1 ? 0 : currentIndex + 1;
    togglePlay(radioList[nextIndex].id);
  };

  /** 音量变化 */
  const handleVolumeChange = (value: number[]) => {
    const newVolume = value[0];
    setVolume(newVolume);
    if (newVolume === 0) {
      setIsMuted(true);
    } else if (isMuted && newVolume > 0) {
      setIsMuted(false);
    }
    if (audioRef.current) {
      audioRef.current.volume = newVolume;
    }
  };

  /** 切换静音 */
  const toggleMute = () => {
    setIsMuted(!isMuted);
    if (audioRef.current) {
      audioRef.current.volume = isMuted ? volume : 0;
    }
  };

  /** 进度跳转 */
  const handleSeek = (value: number[]) => {
    if (audioRef.current && audioRef.current.duration) {
      const time = (value[0] / 100) * audioRef.current.duration;
      audioRef.current.currentTime = time;
      setProgress(value[0]);
    }
  };

  /** 组件卸载时停止播放 */
  useEffect(() => {
    return () => {
      audioRef.current?.pause();
      audioRef.current = null;
    };
  }, []);

  /** 当前播放的电台信息 */
  const currentRadio = playing ? radioList.find((r) => r.id === playing) : null;

  /** 格式化时间 */
  const formatTime = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <PageContainer className="p-0">
      <div className="flex flex-col h-full">
        {/* ========== 头部区域 ========== */}
        <div className="flex items-center gap-3 p-6 pb-4">
          <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="h-9 w-9">
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div className="flex items-center gap-2">
            <Radio className="h-5 w-5 text-[var(--color-primary)]" />
            <h1 className="text-xl font-bold text-[var(--color-card-foreground)]">星尘电台</h1>
          </div>
        </div>

        {/* ========== 大封面图区域（渐变背景） ========== */}
        {currentRadio && (
          <div
            className="mx-6 rounded-2xl p-8 flex flex-col items-center justify-center relative overflow-hidden"
            style={{
              background: "var(--theme-gradient)",
              minHeight: "200px",
            }}
          >
            {/* 装饰性光晕 */}
            <div className="absolute inset-0 bg-white/5" />
            <div className="relative z-10 text-center">
              <div className="text-7xl mb-4 drop-shadow-lg">{currentRadio.cover}</div>
              <h2 className="text-xl font-bold text-white drop-shadow-md">{currentRadio.title}</h2>
              <p className="text-sm text-white/70 mt-1">
                {audioRef.current && !isNaN(audioRef.current.duration)
                  ? `${formatTime(audioRef.current.currentTime)} / ${formatTime(audioRef.current.duration)}`
                  : "正在播放..."}
              </p>
            </div>

            {/* 播放状态脉动动画 */}
            {playing && (
              <div className="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-1.5">
                {[...Array(4)].map((_, i) => (
                  <div
                    key={i}
                    className="w-1 bg-white/80 rounded-full animate-pulse"
                    style={{
                      height: `${12 + Math.random() * 16}px`,
                      animationDelay: `${i * 0.15}s`,
                      animationDuration: "0.6s",
                    }}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* ========== 电台列表（垂直滚动） ========== */}
        <div className="flex-1 overflow-hidden px-6 py-4">
          {isLoading ? (
            <div className="flex items-center justify-center h-full">
              <div className="flex flex-col items-center gap-3 opacity-50">
                <Music className="h-10 w-10 text-[var(--color-muted-foreground)] animate-pulse" />
                <p className="text-sm text-[var(--color-muted-foreground)]">正在加载电台...</p>
              </div>
            </div>
          ) : (
            <ScrollArea className="h-full pr-2">
              <div className="space-y-3 pb-32">
                {radioList.map((radio) => {
                  const isActive = playing === radio.id;
                  return (
                    <div
                      key={radio.id}
                      onClick={() => togglePlay(radio.id)}
                      className={`glass-card rounded-xl p-4 cursor-pointer transition-all duration-300 ${
                        isActive
                          ? "ring-2 ring-[var(--color-primary)] scale-[1.01]"
                          : "hover:scale-[1.005]"
                      }`}
                    >
                      <div className="flex items-center gap-4">
                        {/* 封面缩略图 */}
                        <div
                          className={`w-14 h-14 rounded-xl flex items-center justify-center text-2xl shrink-0 transition-all ${
                            isActive ? "bg-[var(--color-primary)]/20 ring-2 ring-[var(--color-primary)]/50" : "bg-[var(--color-muted)]"
                          }`}
                        >
                          {isActive ? (
                            <Pause className="h-6 w-6 text-[var(--color-primary)]" />
                          ) : (
                            <span>{radio.cover}</span>
                          )}
                        </div>

                        {/* 名称 + 信息 */}
                        <div className="flex-1 min-w-0">
                          <h3
                            className={`font-semibold truncate transition-colors ${
                              isActive
                                ? "text-[var(--color-primary)]"
                                : "text-[var(--color-card-foreground)]"
                            }`}
                          >
                            {radio.title}
                          </h3>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-xs text-[var(--color-muted-foreground)]">
                              {isActive ? "正在播放..." : "点击播放"}
                            </span>
                            {/* 当前播放项高亮脉动 */}
                            {isActive && (
                              <span className="relative flex h-2 w-2">
                                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[var(--color-primary)] opacity-75" />
                                <span className="relative inline-flex rounded-full h-2 w-2 bg-[var(--color-primary)]" />
                              </span>
                            )}
                          </div>
                        </div>

                        {/* 播放按钮 */}
                        <Button
                          variant={isActive ? "default" : "ghost"}
                          size="icon"
                          className={`shrink-0 h-10 w-10 rounded-full ${isActive ? "" : "opacity-60 hover:opacity-100"}`}
                        >
                          {isActive ? (
                            <Pause className="h-5 w-5" />
                          ) : (
                            <Play className="h-5 w-5 ml-0.5" />
                          )}
                        </Button>
                      </div>
                    </div>
                  );
                })}

                {radioList.length === 0 && (
                  <div className="flex flex-col items-center justify-center py-16 opacity-40">
                    <Music className="h-12 w-12 mb-3" />
                    <p className="text-sm text-[var(--color-muted-foreground)]">暂无电台内容</p>
                  </div>
                )}
              </div>
            </ScrollArea>
          )}
        </div>

        {/* ========== 底部播放控制器（chat-input-glass 固定底部） ========== */}
        <div className="chat-input-glass rounded-t-2xl px-6 py-4 shrink-0">
          {/* 进度条 */}
          {currentRadio && (
            <div className="mb-4">
              <Slider
                value={[progress]}
                onValueChange={handleSeek}
                max={100}
                step={0.1}
                className="w-full"
              />
              <div className="flex justify-between mt-1">
                <span className="text-[10px] text-[var(--color-muted-foreground)] tabular-nums">
                  {audioRef.current ? formatTime(audioRef.current.currentTime) : "0:00"}
                </span>
                <span className="text-[10px] text-[var(--color-muted-foreground)] tabular-nums">
                  {audioRef.current && !isNaN(audioRef.current.duration)
                    ? formatTime(audioRef.current.duration)
                    : "--:--"}
                </span>
              </div>
            </div>
          )}

          {/* 控制按钮行 */}
          <div className="flex items-center justify-between">
            {/* 左侧：音量控制 */}
            <div className="flex items-center gap-2 w-32">
              <Button variant="ghost" size="icon" className="h-9 w-9" onClick={toggleMute}>
                {isMuted || volume === 0 ? (
                  <VolumeX className="h-4 w-4 text-[var(--color-muted-foreground)]" />
                ) : (
                  <Volume2 className="h-4 w-4 text-[var(--color-muted-foreground)]" />
                )}
              </Button>
              <Slider
                value={[isMuted ? 0 : volume]}
                onValueChange={handleVolumeChange}
                max={1}
                step={0.01}
                className="flex-1"
              />
            </div>

            {/* 中间：播放控制 */}
            <div className="flex items-center gap-3">
              <Button
                variant="ghost"
                size="icon"
                className="h-11 w-11 rounded-full"
                onClick={playPrev}
                disabled={!playing}
              >
                <SkipBack className="h-5 w-5" />
              </Button>

              <Button
                size="icon"
                className="h-14 w-14 rounded-full shadow-lg"
                style={{
                  background: "var(--theme-gradient)",
                  color: "#fff",
                }}
                onClick={() => {
                  if (currentRadio) {
                    togglePlay(currentRadio.id);
                  } else if (radioList.length > 0) {
                    togglePlay(radioList[0].id);
                  }
                }}
              >
                {playing ? (
                  <Pause className="h-6 w-6" fill="currentColor" />
                ) : (
                  <Play className="h-6 w-6 ml-0.5" fill="currentColor" />
                )}
              </Button>

              <Button
                variant="ghost"
                size="icon"
                className="h-11 w-11 rounded-full"
                onClick={playNext}
                disabled={!playing}
              >
                <SkipForward className="h-5 w-5" />
              </Button>
            </div>

            {/* 右侧：占位保持对称 */}
            <div className="w-32" />
          </div>
        </div>
      </div>
    </PageContainer>
  );
}
