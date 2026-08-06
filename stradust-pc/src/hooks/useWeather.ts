/**
 * useWeather Hook — 天气状态管理
 *
 * 自动获取位置 + 天气，定时刷新，提供响应式状态
 */

import { useState, useEffect, useCallback, useRef } from "react";
import type { WeatherData } from "@/lib/weather";
import { fetchCurrentWeather } from "@/lib/weather";

interface UseWeatherReturn {
  /** 当前天气数据 */
  weather: WeatherData | null;
  /** 是否正在加载 */
  loading: boolean;
  /** 错误信息 */
  error: string | null;
  /** 是否正在下雨/下雪 */
  isRaining: boolean;
  /** 雨量强度 0-1 */
  rainIntensity: number;
  /** 手动刷新 */
  refresh: () => Promise<void>;
}

/** 根据天气代码计算雨量强度 */
function calcRainIntensity(weatherCode: number, precipitation: number): number {
  // 毛毛雨 51-55: 轻微
  if (weatherCode >= 51 && weatherCode <= 55) return 0.25 + precipitation * 0.05;
  // 冻毛毛雨 56-57: 中等
  if (weatherCode === 56 || weatherCode === 57) return 0.3;
  // 小雨 61: 轻-中
  if (weatherCode === 61) return 0.35 + precipitation * 0.08;
  // 中雨 63: 中
  if (weatherCode === 63) return 0.55 + precipitation * 0.06;
  // 大雨 65: 强
  if (weatherCode === 65) return 0.75 + precipitation * 0.04;
  // 冻雨 66-67: 中强
  if (weatherCode === 66 || weatherCode === 67) return 0.5;
  // 阵雨 80-82: 变化大
  if (weatherCode >= 80 && weatherCode <= 82) {
    const base = weatherCode === 80 ? 0.4 : weatherCode === 81 ? 0.6 : 0.8;
    return base + precipitation * 0.05;
  }
  // 阵雪 85-86: 轻微（雪滴效果不同）
  if (weatherCode === 85 || weatherCode === 86) return 0.2;
  // 雷暴 95-99: 很强
  if (weatherCode >= 95) return 0.85 + Math.random() * 0.15;

  return 0;
}

const REFRESH_INTERVAL = 10 * 60 * 1000; // 10 分钟

export function useWeather(autoStart = true): UseWeatherReturn {
  const [weather, setWeather] = useState<WeatherData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await fetchCurrentWeather();
      setWeather(data);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "获取天气失败";
      setError(msg);
      console.warn("[Weather]", msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!autoStart) return;

    // 首次获取
    refresh();

    // 定时刷新
    timerRef.current = setInterval(refresh, REFRESH_INTERVAL);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [autoStart, refresh]);

  const isRaining = weather?.isPrecipitating ?? false;
  const rainIntensity = weather ? calcRainIntensity(weather.weatherCode, weather.precipitation) : 0;

  return {
    weather,
    loading,
    error,
    isRaining,
    rainIntensity,
    refresh,
  };
}
