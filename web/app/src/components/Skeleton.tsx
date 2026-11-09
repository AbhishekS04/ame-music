import type { CSSProperties } from 'react';
import type { SkulletTheme } from 'skullet';
import {
  SkeletonBox,
  Skeleton,
  SkeletonSuspense,
  SkulletProvider,
  useSkullet,
  useReveal,
  type SkeletonBoxProps,
  type SkeletonProps,
  type SkeletonSuspenseProps,
  type SkulletConfig,
} from 'skullet/react';

interface BlockProps {
  width?: CSSProperties['width'];
  height?: CSSProperties['height'];
  radius?: CSSProperties['borderRadius'];
  aspectRatio?: CSSProperties['aspectRatio'];
  className?: string;
  style?: CSSProperties;
}

/** A placeholder box with Skullet shimmer. Purely decorative, so hidden from AT. */
export function SkeletonBlock({ width, height, radius, aspectRatio, className, style }: BlockProps) {
  return (
    <div
      aria-hidden="true"
      className={className ? `shrink-0 overflow-hidden ${className}` : 'shrink-0 overflow-hidden'}
      style={{
        width,
        height,
        borderRadius: radius,
        aspectRatio,
        ...style,
      }}
    >
      <SkeletonBox
        width="100%"
        height="100%"
        radius="inherit"
        className="block h-full w-full"
      />
    </div>
  );
}

interface LineProps {
  /** The text style's line height, so the row is exactly as tall as the text it stands in for. */
  lineHeight: number;
  /** Roughly the font size: the bar's thickness. */
  fontSize: number;
  width: CSSProperties['width'];
  style?: CSSProperties;
}

/** A single line of text in waiting. */
export function SkeletonLine({ lineHeight, fontSize, width, style }: LineProps) {
  return (
    <div aria-hidden="true" className="flex items-center" style={{ height: lineHeight, ...style }}>
      <SkeletonBlock width={width} height={Math.round(fontSize * 0.75)} radius={3} />
    </div>
  );
}

export { SkeletonBox, Skeleton, SkeletonSuspense, SkulletProvider, useSkullet, useReveal };
export type { SkeletonBoxProps, SkeletonProps, SkeletonSuspenseProps, SkulletConfig, SkulletTheme };
