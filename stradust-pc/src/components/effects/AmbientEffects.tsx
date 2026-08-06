import { useEffect, useRef, useCallback } from "react";
import { useSettingsStore } from "@/stores/useSettingsStore";

/**
 * Ambient background with floating orbs
 * Creates a premium atmospheric effect behind all content
 */
export function AmbientBackground() {
  return (
    <div className="ambient-bg" aria-hidden="true">
      <div className="ambient-orb" />
      <div className="ambient-orb" />
      <div className="ambient-orb" />
    </div>
  );
}

/**
 * Rain effect overlay
 * Creates falling rain drops with configurable intensity
 */
export function RainEffect({ intensity = 40 }: { intensity?: number }) {
  const { settings } = useSettingsStore();
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const container = containerRef.current;

    // Clear existing drops
    container.innerHTML = "";

    // Create rain drops
    for (let i = 0; i < intensity; i++) {
      const drop = document.createElement("div");
      drop.className = "rain-drop";
      drop.style.left = `${Math.random() * 100}%`;
      drop.style.height = `${15 + Math.random() * 25}px`;
      drop.style.animationDuration = `${0.5 + Math.random() * 0.5}s`;
      drop.style.animationDelay = `${Math.random() * 2}s`;
      drop.style.opacity = `${0.2 + Math.random() * 0.3}`;
      container.appendChild(drop);
    }
  }, [intensity]);

  const enabled = settings.appearance.theme === ("dark" as string) ||
    document.documentElement.classList.contains("dark");

  if (!enabled) return null;

  return <div ref={containerRef} className="rain-container" aria-hidden="true" />;
}

/**
 * Fog/mist effect overlay
 * Creates drifting fog layers for atmospheric depth
 */
export function FogEffect() {
  const { settings } = useSettingsStore();

  const enabled = settings.appearance.theme === ("dark" as string) ||
    document.documentElement.classList.contains("dark");

  if (!enabled) return null;

  return <div className="fog-layer" aria-hidden="true" />;
}

/**
 * Water ripple effect on click
 * Wrap any element to add ripple on click
 */
export function RippleContainer({ children, className }: { children: React.ReactNode; className?: string }) {
  const containerRef = useRef<HTMLDivElement>(null);

  const handleClick = useCallback((e: React.MouseEvent) => {
    const container = containerRef.current;
    if (!container) return;

    const rect = container.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const ripple = document.createElement("div");
    ripple.className = "ripple-circle";
    ripple.style.left = `${x}px`;
    ripple.style.top = `${y}px`;
    ripple.style.width = "100px";
    ripple.style.height = "100px";
    ripple.style.marginLeft = "-50px";
    ripple.style.marginTop = "-50px";

    container.appendChild(ripple);

    // Clean up after animation
    setTimeout(() => {
      ripple.remove();
    }, 600);
  }, []);

  return (
    <div
      ref={containerRef}
      className={`ripple-container ${className ?? ""}`}
      onClick={handleClick}
    >
      {children}
    </div>
  );
}
