/*
 * SVG displacement-map liquid glass.
 *
 * Ported from the MIT-licensed technique used by kucukkanat/liquid-glass and
 * rdev/liquid-glass-react: a PNG whose red channel encodes horizontal
 * displacement and green channel vertical displacement (128 = neutral), fed
 * to three feDisplacementMap passes at slightly different scales. Applied as
 * backdrop-filter: url(#id) it refracts the live DOM in Chromium; other
 * browsers fall back to the frosted backdrop-filter in global.css.
 *
 * We generate the map from a rounded-box signed distance field, so the bevel
 * points inward along the surface normal exactly as it does on the phone.
 */

export interface DisplacementOptions {
  width: number;
  height: number;
  radius: number;
  bevelDepth: number;
  dpr?: number;
}

function sdRoundedBox(px: number, py: number, halfW: number, halfH: number, r: number): number {
  const qx = Math.abs(px) - halfW + r;
  const qy = Math.abs(py) - halfH + r;
  const outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
  const inside = Math.min(Math.max(qx, qy), 0);
  return inside + outside - r;
}

/** Ken Perlin's smootherstep: 6t⁵ − 15t⁴ + 10t³. */
function smootherstep(edge0: number, edge1: number, x: number): number {
  const t = Math.min(1, Math.max(0, (x - edge0) / (edge1 - edge0)));
  return t * t * t * (t * (t * 6 - 15) + 10);
}

/** RGBA bytes of the displacement map; pure, so it is cheap to unit-test. */
export function computeDisplacementData(options: DisplacementOptions): {
  data: Uint8ClampedArray<ArrayBuffer>;
  width: number;
  height: number;
} {
  const dpr = options.dpr ?? 1;
  const width = Math.max(1, Math.round(options.width * dpr));
  const height = Math.max(1, Math.round(options.height * dpr));
  const halfW = width / 2;
  const halfH = height / 2;
  const radius = Math.min(options.radius * dpr, halfW, halfH);
  const bevel = Math.max(1, options.bevelDepth * dpr);
  const data = new Uint8ClampedArray(new ArrayBuffer(width * height * 4));
  const EPS = 1;

  for (let y = 0; y < height; y += 1) {
    const py = y - halfH + 0.5;
    for (let x = 0; x < width; x += 1) {
      const px = x - halfW + 0.5;
      const i = (y * width + x) << 2;
      const d = sdRoundedBox(px, py, halfW, halfH, radius);
      let nx = 0;
      let ny = 0;
      if (d <= 0 && d >= -bevel) {
        const magnitude = smootherstep(-bevel, 0, d);
        const gx =
          sdRoundedBox(px + EPS, py, halfW, halfH, radius) -
          sdRoundedBox(px - EPS, py, halfW, halfH, radius);
        const gy =
          sdRoundedBox(px, py + EPS, halfW, halfH, radius) -
          sdRoundedBox(px, py - EPS, halfW, halfH, radius);
        const length = Math.hypot(gx, gy) || 1;
        nx = (-gx / length) * magnitude;
        ny = (-gy / length) * magnitude;
      }
      data[i] = 128 + Math.max(-1, Math.min(1, nx)) * 127;
      data[i + 1] = 128 + Math.max(-1, Math.min(1, ny)) * 127;
      data[i + 2] = 128;
      data[i + 3] = 255;
    }
  }
  return { data, width, height };
}

/** The map as a PNG data URL (needs a DOM canvas). */
export function generateDisplacementMap(options: DisplacementOptions): string {
  const { data, width, height } = computeDisplacementData(options);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) return '';
  context.putImageData(new ImageData(data, width, height), 0, 0);
  return canvas.toDataURL('image/png');
}

export interface GlassFilterOptions {
  id: string;
  mapUrl: string;
  width: number;
  height: number;
  /** Base displacement strength, px. */
  scale: number;
  /** Per-channel spread, px (the chromatic fringe). */
  aberration: number;
  /** Frosting blur after refraction, px. */
  frost: number;
}

/** The inner `<filter>` markup; three per-channel displacement passes. */
export function buildFilterMarkup(options: GlassFilterOptions): string {
  const { id, mapUrl, width, height, scale, aberration, frost } = options;
  const sR = scale + aberration;
  const sG = scale;
  const sB = scale - aberration;
  const pad = Math.ceil(sR + 4);
  const px = ((pad / Math.max(1, width)) * 100).toFixed(3);
  const py = ((pad / Math.max(1, height)) * 100).toFixed(3);
  const w = (100 + 2 * Number(px)).toFixed(3);
  const h = (100 + 2 * Number(py)).toFixed(3);

  return `<filter id="${id}" color-interpolation-filters="sRGB" x="-${px}%" y="-${py}%" width="${w}%" height="${h}%">
    <feImage href="${mapUrl}" x="0" y="0" width="${width}" height="${height}" result="map" preserveAspectRatio="none"/>
    <feDisplacementMap in="SourceGraphic" in2="map" scale="${sR}" xChannelSelector="R" yChannelSelector="G" result="dispR"/>
    <feColorMatrix in="dispR" type="matrix" values="1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0" result="red"/>
    <feDisplacementMap in="SourceGraphic" in2="map" scale="${sG}" xChannelSelector="R" yChannelSelector="G" result="dispG"/>
    <feColorMatrix in="dispG" type="matrix" values="0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0" result="green"/>
    <feDisplacementMap in="SourceGraphic" in2="map" scale="${sB}" xChannelSelector="R" yChannelSelector="G" result="dispB"/>
    <feColorMatrix in="dispB" type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0" result="blue"/>
    <feBlend in="red" in2="green" mode="screen" result="rg"/>
    <feBlend in="rg" in2="blue" mode="screen" result="rgb"/>
    <feGaussianBlur in="rgb" stdDeviation="${Math.max(0, frost)}"/>
  </filter>`;
}

/** A zero-sized `<svg>` holding the filter, for injecting into the DOM. */
export function buildFilterSvg(options: GlassFilterOptions): string {
  return `<svg width="0" height="0" aria-hidden="true" style="position:absolute;width:0;height:0;overflow:hidden">${buildFilterMarkup(options)}</svg>`;
}
