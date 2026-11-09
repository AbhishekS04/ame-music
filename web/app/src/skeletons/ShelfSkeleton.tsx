import type { CSSProperties } from 'react';
import { shelfHeader, shelfKinds, shelfList, shelfSection, type ShelfKind } from '@/components/shelf';
import { SkeletonBlock, SkeletonLine } from '@/components/Skeleton';

export type { ShelfKind };

/** How many placeholders fill a shelf at the widest breakpoint, per row. */
const ITEMS: Record<ShelfKind, number> = {
  editorial: 3,
  square: 6,
  tracks: 16,
  horizontal: 8,
  video: 5,
  circle: 10,
};

const caption = 'mt-2 flex flex-col gap-px';
const trackText = 'flex flex-col gap-px px-1';

function Item({ kind, index = 0 }: { kind: ShelfKind; index?: number }) {
  switch (kind) {
    case 'square': {
      const w1 = [80, 68, 88, 72, 84, 64][index % 6];
      const w2 = [55, 45, 60, 50, 40, 55][index % 6];
      return (
        <div className="flex flex-col">
          <SkeletonBlock aspectRatio="1" radius="var(--radius-artwork-medium)" />
          <div className={caption}>
            <SkeletonLine lineHeight={15} fontSize={12} width={`${w1}%`} />
            <SkeletonLine lineHeight={15} fontSize={12} width={`${w2}%`} />
          </div>
        </div>
      );
    }
    case 'video': {
      const w1 = [75, 82, 68, 78, 70][index % 5];
      const w2 = [45, 52, 40, 48, 42][index % 5];
      return (
        <div className="flex flex-col">
          <SkeletonBlock aspectRatio="16 / 9" radius="var(--radius-artwork-medium)" />
          <div className={caption}>
            <SkeletonLine lineHeight={15} fontSize={12} width={`${w1}%`} />
            <SkeletonLine lineHeight={15} fontSize={12} width={`${w2}%`} />
          </div>
        </div>
      );
    }
    case 'circle': {
      const w = [65, 75, 58, 70, 62][index % 5];
      return (
        <div className="flex flex-col">
          <SkeletonBlock aspectRatio="1" radius="50%" />
          <div className="mt-2.5 flex flex-col items-center gap-px">
            <SkeletonLine lineHeight={16} fontSize={13} width={`${w}%`} />
          </div>
        </div>
      );
    }
    case 'tracks': {
      const w1 = [72, 60, 80, 65, 76, 58, 84, 68][index % 8];
      const w2 = [42, 50, 36, 46, 38, 52, 44, 34][index % 8];
      return (
        <div className="grid h-[55px] grid-cols-[40px_1fr] items-center gap-x-3 py-[7.5px] pr-3.5 pl-0">
          <SkeletonBlock width={40} height={40} radius="var(--radius-artwork-small)" />
          <div className={trackText}>
            <SkeletonLine lineHeight={16} fontSize={13} width={`${w1}%`} />
            <SkeletonLine lineHeight={15} fontSize={12} width={`${w2}%`} />
          </div>
        </div>
      );
    }
    case 'horizontal': {
      const w1 = [75, 85, 65, 80][index % 4];
      const w2 = [45, 55, 40, 50][index % 4];
      return (
        <div className="grid grid-cols-[97px_1fr] items-center gap-x-5">
          <SkeletonBlock width={97} height={97} radius="var(--radius-artwork-small)" />
          <div className={trackText}>
            <SkeletonLine lineHeight={20} fontSize={15} width={`${w1}%`} />
            <SkeletonLine lineHeight={15} fontSize={12} width={`${w2}%`} />
          </div>
        </div>
      );
    }
    case 'editorial':
      return (
        <div className="flex flex-col">
          <div className="mt-px mb-[15px] flex flex-col">
            <SkeletonLine lineHeight={14} fontSize={11} width="30%" style={{ marginBottom: 2 }} />
            <SkeletonLine lineHeight={20} fontSize={15} width="45%" />
            <SkeletonLine lineHeight={20} fontSize={15} width="35%" />
          </div>
          <SkeletonBlock aspectRatio="16 / 9" radius="var(--radius-artwork-large)" />
        </div>
      );
  }
}

/** A shelf in waiting: the real shelf's grid, filled with placeholders. */
export function ShelfSkeleton({
  kind,
  title = true,
  titleWidth = 160,
}: {
  kind: ShelfKind;
  title?: boolean;
  titleWidth?: CSSProperties['width'];
}) {
  return (
    <section className={shelfSection} aria-hidden="true">
      {title && (
        <div className={shelfHeader}>
          <SkeletonLine lineHeight={22} fontSize={17} width={titleWidth} />
        </div>
      )}
      <div className="relative">
        <div className={`${shelfList} ${shelfKinds[kind]}`}>
          {Array.from({ length: ITEMS[kind] }, (_, i) => (
            <Item key={i} kind={kind} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
