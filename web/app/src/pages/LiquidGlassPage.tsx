import {
  Check,
  Home,
  LayoutGrid,
  Library,
  MoreHorizontal,
  Radio,
  Search,
} from 'lucide-react';
import { useRef, type CSSProperties, type ReactNode } from 'react';
import { PageHeader } from '@/components/PageHeader';
import { GlassFilterSvg, pointerSheen, resetSheen, useLiquidGlass } from '@/components/glass/useLiquidGlass';
import {
  BackwardIcon,
  ForwardIcon,
  LyricsIcon,
  PlayIcon,
  RepeatIcon,
  ShuffleIcon,
  StarIcon,
  UpNextIcon,
  VolumeIcon,
} from '@/components/icons/Transport';
import { PROFILE_LABELS, PROFILE_ORDER, useGlass, type GlassConfig, type GlassProfile } from '@/state/glass';
import { settingsUi } from './settingsStyles';

/*
 * The phone's GlassSettings page (LIQUID_GLASS_COMPONENT.md §6), recreated
 * for the web. The preview is the phone's stage: hard-edged colour bars under
 * a glass tab bar and a glass player pill, so refraction cues, the rim and
 * the film all show. Every control writes state/glass.ts, which drives the
 * real player pill too.
 *
 * A note on fidelity: the phone samples a recorded backdrop in an AGSL lens
 * shader. The web cannot sample the DOM through SVG in backdrop-filter
 * (Chrome ignores it), so the lens maps to its visible cues — rim thickness,
 * caustic, dispersion and depth. The controls behave exactly as on the phone.
 */

function Section({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <>
      {title && <h2 className={settingsUi.sectionHeader}>{title}</h2>}
      <section className={settingsUi.group}>{children}</section>
    </>
  );
}

function Slider({
  label,
  value,
  min,
  max,
  step = 0.01,
  readout,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  readout: (value: number) => string;
  onChange: (value: number) => void;
}) {
  const fill = ((value - min) / (max - min)) * 100;
  return (
    <label className="relative block px-4 py-[11px] hairline-[16px]">
      <span className="flex items-center justify-between gap-3 type-title-3">
        <span>{label}</span>
        <span className="type-body text-secondary tabular-nums">{readout(value)}</span>
      </span>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.currentTarget.value))}
        className="glass-range mt-2.5"
        style={{ '--fill': `${fill}%` } as CSSProperties}
      />
    </label>
  );
}

function ChoiceRow({ label, selected, onClick }: { label: string; selected: boolean; onClick: () => void }) {
  return (
    <button type="button" aria-pressed={selected} onClick={onClick} className={`${settingsUi.controlRow} w-full text-start`}>
      <span className={selected ? 'text-primary' : 'text-secondary'}>{label}</span>
      {selected && <Check size={18} className="text-key" />}
    </button>
  );
}

function SwitchRow({ label, checked, onChange }: { label: string; checked: boolean; onChange: (on: boolean) => void }) {
  return (
    <label className={settingsUi.controlRow}>
      <span>{label}</span>
      <input type="checkbox" className={settingsUi.switch} checked={checked} onChange={(event) => onChange(event.currentTarget.checked)} />
    </label>
  );
}

const tintPresets = [0, 32, 52, 130, 186, 220, 268, 310];

function TintControl() {
  const tintHue = useGlass((s) => s.tintHue);
  const tintStrength = useGlass((s) => s.tintStrength);
  const set = useGlass((s) => s.set);
  const setTintHue = useGlass((s) => s.setTintHue);
  const on = tintHue !== null;
  const hue = tintHue ?? 0;
  return (
    <div className="relative px-4 py-[11px]">
      <div className="flex items-center justify-between gap-3">
        <span className="type-title-3">Tint</span>
        <button
          type="button"
          disabled={!on}
          onClick={() => setTintHue(null)}
          className="type-body text-key disabled:text-secondary"
        >
          {on ? 'Clear' : 'None'}
        </button>
      </div>
      <input
        type="range"
        min={0}
        max={360}
        step={1}
        value={hue}
        onChange={(event) => setTintHue(Number(event.currentTarget.value))}
        aria-label="Tint hue"
        className="glass-range mt-3"
        style={{
          background:
            'linear-gradient(to right, hsl(0 80% 55%), hsl(60 80% 55%), hsl(120 80% 55%), hsl(180 80% 55%), hsl(240 80% 55%), hsl(300 80% 55%), hsl(360 80% 55%))',
        }}
      />
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {tintPresets.map((preset) => {
          const selected = on && Math.round(hue) === preset;
          return (
            <button
              key={preset}
              type="button"
              aria-label={`Tint ${preset} degrees`}
              aria-pressed={selected}
              onClick={() => setTintHue(preset)}
              className={`size-6 rounded-full ${selected ? 'ring-2 ring-primary ring-offset-2 ring-offset-[var(--glass-tint-base)]' : ''}`}
              style={{ backgroundColor: `hsl(${preset} 80% 55%)` }}
            />
          );
        })}
      </div>
      {on && (
        <div className="-mx-4">
          <Slider
            label="Tint Strength"
            value={tintStrength}
            min={0}
            max={1}
            onChange={(value) => set({ tintStrength: value })}
            readout={(value) => `${Math.round(value * 100)}%`}
          />
        </div>
      )}
    </div>
  );
}

/** The phone's GlassPreview: hard colour bars under the reference tab bar and the player pill. */
function GlassPreview({ config }: { config: GlassConfig }) {
  return (
    // The 40px gutter leaves room for the lens to reach outside each pill.
    <div
      className="relative isolate overflow-hidden rounded-[20px] bg-[#1b1b1b] px-10 py-10"
      style={{ backgroundImage: 'linear-gradient(115deg, var(--keyColor) 0%, #ff7a45 32%, #2ed3c6 66%, #1b1b1b 100%)' }}
    >
      <div className="absolute inset-0 -z-1 flex flex-col justify-evenly" aria-hidden="true">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className={`h-[5px] ${i % 2 === 0 ? 'w-[94%] bg-white/85' : 'w-[70%] bg-black/45'}`} />
        ))}
      </div>
      <div className="relative flex flex-col gap-3">
        <PreviewPlayer config={config} />
        <PreviewTabs config={config} />
      </div>
    </div>
  );
}

/** The web player pill, as it appears over a page: the reference for the material. */
function PreviewPlayer({ config }: { config: GlassConfig }) {
  const ref = useRef<HTMLDivElement>(null);
  const glass = useLiquidGlass(ref, config);
  return (
    <div
      ref={ref}
      className="glass-pill relative grid h-14 w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 rounded-pill px-4"
      onPointerMove={pointerSheen}
      onPointerLeave={resetSheen}
      style={
        glass.backdropFilter
          ? ({ backdropFilter: glass.backdropFilter, WebkitBackdropFilter: glass.backdropFilter } as CSSProperties)
          : undefined
      }
    >
      <GlassFilterSvg svg={glass.svg} />
      <div className="flex items-center gap-1 text-primary">
        <ShuffleIcon size={22} />
        <BackwardIcon size={22} />
        <PlayIcon size={24} />
        <ForwardIcon size={22} />
        <RepeatIcon size={22} />
      </div>
      <div className="flex min-w-0 items-center gap-2.5">
        <div className="size-[34px] flex-none rounded-[6px] bg-[linear-gradient(135deg,#f43f5e,#f59e0b)] shadow-[inset_0_0_0_0.5px_rgb(0_0_0/0.25)]" />
        <div className="min-w-0 flex-1 pb-0.5">
          <div className="truncate type-body-emphasized">Die For You (Remix)</div>
          <div className="truncate type-callout text-secondary">The Weeknd, Ariana Grande</div>
          <div className="mt-1 h-0.5 w-full overflow-hidden rounded-[1px] bg-black/20">
            <div className="h-full w-1/3 bg-white/60" />
          </div>
        </div>
      </div>
      <div className="flex items-center gap-2 text-primary">
        <StarIcon size={18} />
        <MoreHorizontal size={18} />
        <LyricsIcon size={18} />
        <UpNextIcon size={18} />
        <VolumeIcon size={18} level={1} />
      </div>
    </div>
  );
}

const tabs = [
  { label: 'Home', Icon: Home },
  { label: 'New', Icon: LayoutGrid },
  { label: 'Radio', Icon: Radio },
  { label: 'Library', Icon: Library },
  { label: 'Search', Icon: Search },
];

/** The phone's reference tab bar, with the selected tab's puck. */
function PreviewTabs({ config }: { config: GlassConfig }) {
  const ref = useRef<HTMLDivElement>(null);
  const glass = useLiquidGlass(ref, config);
  return (
    <div
      ref={ref}
      className="glass-pill flex h-[52px] items-center gap-1 rounded-pill px-2"
      onPointerMove={pointerSheen}
      onPointerLeave={resetSheen}
      style={
        glass.backdropFilter
          ? ({ backdropFilter: glass.backdropFilter, WebkitBackdropFilter: glass.backdropFilter } as CSSProperties)
          : undefined
      }
    >
      <GlassFilterSvg svg={glass.svg} />
      {tabs.map(({ label, Icon }, i) => (
        <div
          key={label}
          className="flex h-10 flex-1 flex-col items-center justify-center gap-0.5 rounded-pill"
          style={i === 0 ? { backgroundColor: `rgb(255 255 255 / ${config.puckOpacity})` } : undefined}
        >
          <Icon size={19} strokeWidth={2} className="text-primary" />
          <span className="type-[600_10px/12px] text-primary">{label}</span>
        </div>
      ))}
    </div>
  );
}

export default function LiquidGlassPage() {
  const config = useGlass();
  const liquid = config.profile !== 'performance';

  return (
    <div className={settingsUi.centered}>
      <PageHeader title="Liquid Glass" />
      <div className={settingsUi.body}>
        <p className="mx-4 mt-1 mb-3 type-body-tall text-secondary">
          The material the player pill and the tab bars are made of: how much it blurs, how bright its rim is, how deep the lens
          feels, and what it is tinted with. The preview below is live; the player at the bottom uses the same settings.
        </p>

        <div className="sticky top-0 z-20 -mx-(--body-gutter) bg-page px-(--body-gutter) py-3">
          <GlassPreview config={config} />
        </div>

        <Section title="Material">
          {PROFILE_ORDER.map((profile: GlassProfile) => (
            <ChoiceRow
              key={profile}
              label={PROFILE_LABELS[profile]}
              selected={config.profile === profile}
              onClick={() => config.setProfile(profile)}
            />
          ))}
          <Slider
            label="Vibrancy"
            value={config.vibrancy}
            min={0}
            max={2}
            onChange={(value) => config.set({ vibrancy: value })}
            readout={(value) => `${Math.round(value * 50)}%`}
          />
          {liquid && (
            <Slider
              label="Blur Radius"
              value={config.blurRadius}
              min={0}
              max={24}
              step={1}
              onChange={(value) => config.set({ blurRadius: value })}
              readout={(value) => `${Math.round(value)} dp`}
            />
          )}
        </Section>

        <Section title="Colour & Film">
          <TintControl />
          <Slider
            label="Surface Opacity"
            value={config.surfaceOpacity}
            min={0}
            max={1}
            onChange={(value) => config.set({ surfaceOpacity: value })}
            readout={(value) => `${Math.round(value * 100)}%`}
          />
          <Slider
            label="Puck Indicator Opacity"
            value={config.puckOpacity}
            min={0}
            max={1}
            onChange={(value) => config.set({ puckOpacity: value })}
            readout={(value) => `${Math.round(value * 100)}%`}
          />
        </Section>

        {liquid && (
          <Section title="Refraction & Edges">
            <Slider
              label="Specular Rim"
              value={config.highlightOpacity}
              min={0}
              max={1}
              onChange={(value) => config.set({ highlightOpacity: value })}
              readout={(value) => `${Math.round(value * 100)}%`}
            />
            <Slider
              label="Refraction Depth"
              value={config.lensHeight}
              min={0}
              max={1}
              onChange={(value) => config.set({ lensHeight: value })}
              readout={(value) => `${Math.round(value * 48)} dp`}
            />
            <Slider
              label="Refraction Amount"
              value={config.lensAmount}
              min={0}
              max={1}
              onChange={(value) => config.set({ lensAmount: value })}
              readout={(value) => `${Math.round(value * 48)} dp`}
            />
            <SwitchRow
              label="Chromatic Dispersion"
              checked={config.chromaticAberration}
              onChange={(on) => config.set({ chromaticAberration: on })}
            />
            <SwitchRow label="Depth Pinch" checked={config.depthEffect} onChange={(on) => config.set({ depthEffect: on })} />
          </Section>
        )}

        <section className={`${settingsUi.group} mt-7`}>
          <button type="button" onClick={config.reset} className={`${settingsUi.controlRow} w-full text-start type-title-3`}>
            Reset to Defaults
          </button>
        </section>
      </div>
    </div>
  );
}
