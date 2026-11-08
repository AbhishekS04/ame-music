import { create } from 'zustand';

/*
 * The phone's Liquid Glass settings (data/GlassStore.kt + ui/settings/GlassSettings.kt),
 * as a web store. The values drive CSS custom properties on <html>, which the
 * player pill and the glass page's reference tab bar read, so a tweak in the
 * settings page is the same tweak the player uses.
 *
 * The phone runs an AGSL shader over a recorded backdrop. The web samples the
 * page with backdrop-filter, which can't do a real lens (Chrome ignores SVG
 * filters there), so the lens parameters map onto the closest cues: the wet
 * rim's width, the caustic's strength and the depth vignette.
 */

export type GlassProfile = 'performance' | 'balanced' | 'quality';
export type GlassStyle = 'liquid' | 'blur' | 'transparent';

export interface GlassConfig {
  profile: GlassProfile;
  /** 0–2, as on the phone: the backdrop's saturation. */
  vibrancy: number;
  /** 0–24, as on the phone's slider; the CSS blur is twice this. */
  blurRadius: number;
  /** 0–1 of the 48dp refraction depth. */
  lensHeight: number;
  /** 0–1 of the 48dp refraction amount. */
  lensAmount: number;
  chromaticAberration: boolean;
  depthEffect: boolean;
  /** The film's alpha (GlassEffectConfig.surfaceOpacity). */
  surfaceOpacity: number;
  /** The specular rim's alpha (highlightOpacity). */
  highlightOpacity: number;
  /** The selected tab's puck alpha (puckOpacity). */
  puckOpacity: number;
  /** null = no tint, as NO_TINT on the phone. */
  tintHue: number | null;
  tintStrength: number;
}

/** The phone's Balanced profile, exactly (GlassProfile.Balanced). */
export const GLASS_DEFAULTS: GlassConfig = {
  profile: 'balanced',
  vibrancy: 1.2,
  blurRadius: 2,
  lensHeight: 0.4,
  lensAmount: 0.6,
  chromaticAberration: false,
  depthEffect: false,
  surfaceOpacity: 0.35,
  highlightOpacity: 0.55,
  puckOpacity: 0.26,
  tintHue: null,
  tintStrength: 0.5,
};

/** The phone's three profiles (GlassProfile in data/GlassStore.kt). */
export const PROFILE_PRESETS: Record<GlassProfile, Partial<GlassConfig>> = {
  performance: {
    vibrancy: 1,
    blurRadius: 0,
    lensHeight: 0,
    lensAmount: 0,
    chromaticAberration: false,
    depthEffect: false,
    surfaceOpacity: 0.55,
    highlightOpacity: 0.3,
  },
  balanced: {
    vibrancy: 1.2,
    blurRadius: 2,
    lensHeight: 0.4,
    lensAmount: 0.6,
    chromaticAberration: false,
    depthEffect: false,
    surfaceOpacity: 0.35,
    highlightOpacity: 0.55,
  },
  quality: {
    vibrancy: 1.4,
    blurRadius: 3,
    lensHeight: 0.5,
    lensAmount: 0.8,
    chromaticAberration: true,
    depthEffect: false,
    surfaceOpacity: 0.30,
    highlightOpacity: 0.65,
  },
};

export const PROFILE_LABELS: Record<GlassProfile, string> = {
  performance: 'Performance',
  balanced: 'Balanced',
  quality: 'Quality',
};

export const PROFILE_ORDER: GlassProfile[] = ['performance', 'balanced', 'quality'];

export function glassStyle(profile: GlassProfile): GlassStyle {
  return profile === 'performance' ? 'transparent' : 'liquid';
}

// v2: the SVG-displacement implementation. The old key held values tuned for
// the CSS-only version, so this one starts from the phone's Balanced profile.
const STORAGE_KEY = 'ame:glass:v2';

interface GlassState extends GlassConfig {
  setProfile: (profile: GlassProfile) => void;
  set: (patch: Partial<GlassConfig>) => void;
  setTintHue: (hue: number | null) => void;
  reset: () => void;
}

function pickConfig(state: GlassConfig): GlassConfig {
  const { profile, vibrancy, blurRadius, lensHeight, lensAmount, chromaticAberration, depthEffect,
    surfaceOpacity, highlightOpacity, puckOpacity, tintHue, tintStrength } = state;
  return { profile, vibrancy, blurRadius, lensHeight, lensAmount, chromaticAberration, depthEffect,
    surfaceOpacity, highlightOpacity, puckOpacity, tintHue, tintStrength };
}

function load(): GlassConfig {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}') as Partial<GlassConfig>;
    return { ...GLASS_DEFAULTS, ...saved };
  } catch {
    return GLASS_DEFAULTS;
  }
}

function save(config: GlassConfig): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config));
  } catch {
    // A private window without storage: the session still works, it just forgets.
  }
}

export const useGlass = create<GlassState>()((set, get) => ({
  ...load(),

  setProfile(profile) {
    if (profile === get().profile) return;
    set({ profile, ...PROFILE_PRESETS[profile] });
  },

  set(patch) {
    set(patch);
  },

  setTintHue(tintHue) {
    set({ tintHue });
  },

  reset() {
    set({ ...GLASS_DEFAULTS });
  },
}));

/**
 * The custom properties the stylesheets read. Everything the phone's config
 * exposes is here; a few values (the inner stroke, the outline) stay with the
 * theme tokens so light and dark keep their contrast.
 */
export function glassCssVars(config: GlassConfig): Record<`--${string}`, string> {
  const liquid = config.profile !== 'performance';
  const blur = liquid ? Math.round(config.blurRadius * 6) : 0;
  const saturate = liquid ? 1 + 0.5 * config.vibrancy : 1;
  const brightness = liquid ? 1 + 0.04 * config.vibrancy : 1;
  const lens = liquid ? config.lensAmount : 0;
  const rim = liquid ? 1 + config.lensHeight * 2 : 1;
  const highlight = liquid ? config.highlightOpacity : 0.15;
  return {
    '--glass-blur': `${blur}px`,
    '--glass-saturate': saturate.toFixed(3),
    '--glass-brightness': brightness.toFixed(3),
    '--glass-tint-alpha': config.surfaceOpacity.toFixed(3),
    '--glass-highlight-alpha': highlight.toFixed(3),
    '--glass-specular': `rgb(255 255 255 / ${highlight.toFixed(3)})`,
    '--glass-specular-soft': `rgb(255 255 255 / ${(highlight * 0.55).toFixed(3)})`,
    '--glass-rim-width': `${rim.toFixed(2)}px`,
    '--glass-caustic': `rgb(255 255 255 / ${(liquid ? 0.04 + lens * 0.18 : 0).toFixed(3)})`,
    '--glass-depth': (config.depthEffect ? 0.1 + lens * 0.14 : 0).toFixed(3),
    '--glass-chromatic-cool': config.chromaticAberration && liquid ? 'rgb(120 200 255 / 0.35)' : 'transparent',
    '--glass-puck-opacity': config.puckOpacity.toFixed(3),
    '--glass-tint-hue': String(config.tintHue ?? 0),
    '--glass-tint-strength': config.tintStrength.toFixed(3),
    '--glassMaterialBackdropFilter': liquid ? `saturate(${saturate.toFixed(2)}) blur(${Math.min(blur, 40)}px)` : 'none',
  };
}

/** Writes the settings onto <html>; called on load and on every change. */
export function applyGlassTheme(config: GlassConfig): void {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  for (const [name, value] of Object.entries(glassCssVars(config))) {
    root.style.setProperty(name, value);
  }
  root.dataset.glassStyle = glassStyle(config.profile);
  if (config.tintHue === null) delete root.dataset.glassTint;
  else root.dataset.glassTint = 'on';
}

// Applied at import so a saved setting paints on the first frame, and kept in
// step after that. The player pill, the sidebar and the settings preview all
// read the same variables.
applyGlassTheme(useGlass.getState());
useGlass.subscribe((state) => {
  const config = pickConfig(state);
  save(config);
  applyGlassTheme(config);
});

// For browser-driven tests, next to __bcPlayer.
if (import.meta.env.DEV) Object.assign(window, { __bcGlass: useGlass });
