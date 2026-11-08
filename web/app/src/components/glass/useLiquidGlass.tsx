import { useEffect, useId, useMemo, useState, type PointerEvent as ReactPointerEvent, type RefObject } from 'react';
import { buildFilterSvg, generateDisplacementMap } from '@/lib/glassFilter';
import type { GlassConfig } from '@/state/glass';

function hash(value: string): string {
  let h = 0;
  for (let i = 0; i < value.length; i += 1) h = (Math.imul(31, h) + value.charCodeAt(i)) | 0;
  return (h >>> 0).toString(36);
}

/**
 * Builds the displacement map + SVG filter for one glass element and returns
 * the inline `backdrop-filter` that makes Chromium refract the live page.
 * The element is measured, so each pill gets a map matched to its own shape.
 *
 * Values map straight from the phone's config: lensAmount → displacement
 * strength, lensHeight → bevel band, blurRadius → frost, vibrancy →
 * saturation, and chromatic dispersion → the per-channel spread.
 */
export function useLiquidGlass(ref: RefObject<HTMLElement | null>, config: GlassConfig): {
  svg: string;
  backdropFilter: string | undefined;
} {
  const [size, setSize] = useState({ width: 0, height: 0 });

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const measure = () => {
      const rect = element.getBoundingClientRect();
      const width = Math.round(rect.width);
      const height = Math.round(rect.height);
      setSize((previous) => (previous.width === width && previous.height === height ? previous : { width, height }));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref]);

  const flat = config.profile === 'performance';
  const radius = Math.max(1, size.height / 2);
  const bevel = flat ? 0 : Math.min(radius, Math.max(8, config.lensHeight * 80));
  const scale = flat ? 0 : config.lensAmount * 130;
  const aberration = flat || !config.chromaticAberration ? 0 : Math.max(4, config.lensAmount * 12);
  const frost = flat ? 0 : config.blurRadius * 1.8;
  const saturation = 1 + 0.5 * config.vibrancy;

  const mapUrl = useMemo(() => {
    if (flat || size.width < 2 || size.height < 2) return '';
    return generateDisplacementMap({
      width: size.width,
      height: size.height,
      radius,
      bevelDepth: bevel,
      dpr: typeof window === 'undefined' ? 1 : window.devicePixelRatio || 1,
    });
  }, [flat, size.width, size.height, radius, bevel]);

  const base = useId().replace(/:/g, '');
  const filterId = useMemo(
    () => `glass-${base}-${hash(`${size.width}x${size.height}-${radius}-${bevel}-${scale}-${aberration}-${frost}`)}`,
    [base, size.width, size.height, radius, bevel, scale, aberration, frost],
  );

  const svg = useMemo(() => {
    if (!mapUrl) return '';
    return buildFilterSvg({ id: filterId, mapUrl, width: size.width, height: size.height, scale, aberration, frost });
  }, [filterId, mapUrl, size.width, size.height, scale, aberration, frost]);

  const backdropFilter = mapUrl ? `url(#${filterId}) saturate(${saturation.toFixed(2)})` : undefined;
  return { svg, backdropFilter };
}

/** Moves the glossy sheen to the pointer, as the popular web glasses do. */
export function pointerSheen(event: ReactPointerEvent<HTMLElement>): void {
  const rect = event.currentTarget.getBoundingClientRect();
  event.currentTarget.style.setProperty('--glass-pointer-x', `${((event.clientX - rect.left) / rect.width) * 100}%`);
  event.currentTarget.style.setProperty('--glass-pointer-y', `${((event.clientY - rect.top) / rect.height) * 100}%`);
}

export function resetSheen(event: ReactPointerEvent<HTMLElement>): void {
  event.currentTarget.style.setProperty('--glass-pointer-x', '18%');
  event.currentTarget.style.setProperty('--glass-pointer-y', '0%');
}

/** Injects the filter markup; zero-sized and out of flow, so grids ignore it. */
export function GlassFilterSvg({ svg }: { svg: string }) {
  if (!svg) return null;
  return (
    <div
      aria-hidden="true"
      style={{ position: 'absolute', width: 0, height: 0, overflow: 'hidden', pointerEvents: 'none' }}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
