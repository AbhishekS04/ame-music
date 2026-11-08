import { useEffect, useRef, useState } from 'react';
import { artworkAtPixels } from '@/lib/artwork';

const LAYER = 'absolute inset-0 size-full object-cover';

/**
 * The now-playing page's backdrop, drawn from the track's own sleeve: the
 * artwork enlarged past the window, blurred and saturated, with a second,
 * wider bloom under it and cinematic scrims over it. It cross-fades track to
 * track, so the page always reads as one continuous colour rather than a
 * random palette. A paused sleeve holds still.
 */
export function ArtworkBackdrop({ artworkUrl, playing }: { artworkUrl: string | null; playing: boolean }) {
  const [current, setCurrent] = useState<string | null>(artworkUrl);
  const [previous, setPrevious] = useState<string | null>(null);
  const clearPrevious = useRef<number | null>(null);

  useEffect(() => {
    if (artworkUrl === current) return;
    setPrevious(current);
    setCurrent(artworkUrl);
    if (clearPrevious.current) window.clearTimeout(clearPrevious.current);
    clearPrevious.current = window.setTimeout(() => setPrevious(null), 900);
  }, [artworkUrl, current]);

  useEffect(
    () => () => {
      if (clearPrevious.current) window.clearTimeout(clearPrevious.current);
    },
    [],
  );

  return (
    <div className="absolute inset-0 -z-1 overflow-hidden bg-[#121216]" aria-hidden="true">
      {previous && <Layer key={previous} url={previous} />}
      {current && <Layer key={current} url={current} live={playing} />}

      {/* Chrome at the top, controls at the foot, and a vignette for the lyrics. */}
      <div className="absolute inset-0 bg-[linear-gradient(180deg,rgb(0_0_0/0.55),transparent_22%,transparent_62%,rgb(0_0_0/0.82))]" />
      <div className="absolute inset-0 bg-[radial-gradient(120%_80%_at_50%_45%,transparent_30%,rgb(0_0_0/0.5)_100%)]" />
    </div>
  );
}

function Layer({ url, live = false }: { url: string; live?: boolean }) {
  const src = artworkAtPixels(url, 300);
  return (
    <div className="absolute inset-0 animate-fade-in">
      <img
        src={src}
        alt=""
        referrerPolicy="no-referrer"
        decoding="async"
        className={`${LAYER} scale-125 blur-[90px] saturate-150 ${live ? 'animate-[artwork-drift_36s_ease-in-out_infinite_alternate]' : ''}`}
      />
      {/* A wider, softer bloom so the edges never end in a hard line. */}
      <img
        src={src}
        alt=""
        referrerPolicy="no-referrer"
        decoding="async"
        className={`${LAYER} scale-150 opacity-40 blur-[140px] saturate-200`}
      />
    </div>
  );
}
