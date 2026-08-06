/**
 * 天气服务 — 获取地理位置 + 天气数据
 * 使用 Open-Meteo 免费 API（无需 API Key）
 *
 * 国内网络优化：
 * - 地理编码失败不影响天气获取（解耦设计）
 * - 多个地理编码备选源
 * - 超时控制严格
 */

export interface WeatherData {
  /** 城市名称 */
  city: string;
  /** 纬度 */
  latitude: number;
  /** 经度 */
  longitude: number;
  /** 温度 (°C) */
  temperature: number;
  /** 体感温度 (°C) */
  feelsLike: number;
  /** 湿度 (%) */
  humidity: number;
  /** 风速 (km/h) */
  windSpeed: number;
  /** 天气代码 (WMO) */
  weatherCode: number;
  /** 天气描述 */
  description: string;
  /** 是否在下雨/下雪 */
  isPrecipitating: boolean;
  /** 降水强度 (mm/h) */
  precipitation: number;
  /** 获取时间戳 */
  fetchedAt: number;
}

/** WMO 天气代码 → 中文描述 */
const WMO_CODES: Record<number, string> = {
  0: "晴朗",
  1: "大部晴朗",
  2: "多云",
  3: "阴天",
  45: "雾",
  48: "雾凇",
  51: "毛毛雨（小）",
  53: "毛毛雨（中）",
  55: "毛毛雨（大）",
  56: "冻毛毛雨（小）",
  57: "冻毛毛雨（大）",
  61: "小雨",
  63: "中雨",
  65: "大雨",
  66: "冻雨（小）",
  67: "冻雨（大）",
  71: "小雪",
  73: "中雪",
  75: "大雪",
  77: "雪粒",
  80: "阵雨（小）",
  81: "阵雨（中）",
  82: "阵雨（大）",
  85: "阵雪（小）",
  86: "阵雪（大）",
  95: "雷暴",
  96: "雷暴伴冰雹",
  99: "强雷暴伴冰雹",
};

/** 判断是否为降水天气 (WMO codes 51-57, 61-67, 80-82, 85-86, 95-99) */
function isPrecipitation(code: number): boolean {
  return (
    (code >= 51 && code <= 57) ||
    (code >= 61 && code <= 67) ||
    (code >= 80 && code <= 82) ||
    (code >= 85 && code <= 86) ||
    (code >= 95 && code <= 99)
  );
}

/** 缓存：避免频繁请求 */
let cachedWeather: WeatherData | null = null;
const CACHE_TTL = 10 * 60 * 1000; // 10 分钟缓存

/**
 * 反向地理编码 — 获取城市名（纯辅助功能，失败不影响任何主流程）
 * 多个备选源，按顺序尝试
 */
async function reverseGeocode(lat: number, lon: number): Promise<string> {
  const sources: Array<{ url: string; parser: (data: any) => string }> = [
    {
      url: `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=10&accept-language=zh-CN`,
      parser: (d) =>
        d.address?.city ?? d.address?.town ?? d.address?.county ?? d.address?.state ?? "",
    },
  ];

  for (const source of sources) {
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 3000); // 3 秒硬超时

      const res = await fetch(source.url, {
        signal: controller.signal,
        headers: { "User-Agent": "StradustApp/1.0" },
      });
      clearTimeout(timer);

      if (res.ok) {
        const data = await res.json();
        const city = source.parser(data);
        if (city) return city;
      }
    } catch {
      // 这个源失败了，尝试下一个
      continue;
    }
  }

  // 所有源都失败，返回空字符串（不是错误！）
  return "";
}

/**
 * 获取用户地理位置（仅经纬度，不含地理编码）
 */
export async function getLocation(): Promise<{ latitude: number; longitude: number }> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error("浏览器不支持地理位置"));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
      },
      (error) => {
        switch (error.code) {
          case error.PERMISSION_DENIED:
            reject(new Error("位置权限被拒绝"));
            break;
          case error.POSITION_UNAVAILABLE:
            reject(new Error("无法获取位置信息"));
            break;
          case error.TIMEOUT:
            reject(new Error("获取位置超时"));
            break;
          default:
            reject(new Error("获取位置失败"));
        }
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 5 * 60 * 1000,
      }
    );
  });
}

/**
 * 获取当前天气
 * @param lat 纬度
 * @param lon 经度
 */
export async function getWeather(lat: number, lon: number): Promise<WeatherData> {
  // 检查缓存
  if (cachedWeather && Date.now() - cachedWeather.fetchedAt < CACHE_TTL) {
    return cachedWeather;
  }

  const params = new URLSearchParams({
    latitude: String(lat),
    longitude: String(lon),
    current:
      "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,windspeed_10m",
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  });

  const res = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`);
  if (!res.ok) throw new Error(`天气API请求失败: ${res.status}`);

  const data = await res.json();
  const current = data.current;
  const code = current.weather_code;

  const weather: WeatherData = {
    city: "", // 先留空，异步填充
    latitude: lat,
    longitude: lon,
    temperature: Math.round(current.temperature_2m),
    feelsLike: Math.round(current.apparent_temperature),
    humidity: current.relative_humidity_2m,
    windSpeed: Math.round(current.windspeed_10m),
    weatherCode: code,
    description: WMO_CODES[code] ?? "未知天气",
    isPrecipitating: isPrecipitation(code),
    precipitation: current.precipitation ?? 0,
    fetchedAt: Date.now(),
  };

  // 异步补充城市名（不阻塞返回！）
  reverseGeocode(lat, lon)
    .then((city) => {
      if (city && cachedWeather === weather) {
        cachedWeather = { ...weather, city };
      }
    })
    .catch(() => {
      // 城市名获取完全失败，忽略
    });

  cachedWeather = weather;

  // 控制台输出天气状态（方便调试）
  console.log(
    `[Weather] ${weather.description} | ${weather.temperature}°C | 降水:${weather.precipitation}mm/h | 下雨:${weather.isPrecipitating}`
  );

  return weather;
}

/**
 * 一键获取：定位 + 天气
 * 地理编码失败不影响天气数据返回
 */
export async function fetchCurrentWeather(): Promise<WeatherData> {
  const location = await getLocation();
  return getWeather(location.latitude, location.longitude);
}
