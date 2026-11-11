import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { Delete, Lock, LockOpen, RotateCcw } from 'lucide-react';
import { compartmentStateLabel, sizeLabel } from '../api/labels';
import type { CompartmentState, SizeClass } from '../api/types';

export interface Door3D {
  number: number;
  size: SizeClass;
  state: CompartmentState;
  doorOpen: boolean;
  locked: boolean;
  mealPresent: boolean;
  mine?: boolean;
  orderId?: number | null;
  fault?: boolean;
}

interface Props {
  name: string;
  doors: Door3D[];
  selected?: number | null;
  onSelect?: (number: number) => void;
  /** Texte de l'ecran du coffre. */
  display: string;
  /** Chiffres deja saisis (affiches masques). */
  code: string;
  /** Clavier actif : sans ces fonctions, les touches sont inertes. */
  onDigit?: (digit: string) => void;
  onErase?: () => void;
  onSubmit?: () => void;
  online?: boolean;
}

const W = 540, H = 260, D = 210, PANEL = 168, COLS = 3;
const led: Record<CompartmentState, string> = {
  AVAILABLE: '#22c55e', RESERVED: '#38bdf8', LOADING: '#fb923c', OCCUPIED: '#fb923c', LOCKED: '#f8fafc', WITHDRAWING: '#fb923c', OPEN: '#fb923c', FAULT: '#ef4444',
};
const shell = 'linear-gradient(160deg,#23332c 0%,#14201b 100%)';

/**
 * Coffre en 3D (transformations CSS) : six portes sur charnieres qui pivotent a l'ouverture, cavites avec le repas,
 * ecran et clavier sur la facade. Faites glisser pour tourner le coffre.
 */
export function Box3D({ name, doors, selected, onSelect, display, code, onDigit, onErase, onSubmit, online = true }: Props) {
  const wrap = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  const [rot, setRot] = useState({ x: -14, y: -26 });
  const drag = useRef<{ x: number; y: number; rx: number; ry: number; moved: boolean } | null>(null);

  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const fit = () => setScale(Math.min(1, el.clientWidth / (W + 150)));
    fit();
    const observer = new ResizeObserver(fit);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  const down = (e: ReactPointerEvent) => { drag.current = { x: e.clientX, y: e.clientY, rx: rot.x, ry: rot.y, moved: false }; };
  const move = (e: ReactPointerEvent) => {
    const d = drag.current;
    if (!d || e.buttons === 0) return;
    const dx = e.clientX - d.x, dy = e.clientY - d.y;
    if (!d.moved && Math.hypot(dx, dy) < 5) return;
    d.moved = true;
    setRot({ x: Math.max(-55, Math.min(25, d.rx - dy * 0.4)), y: Math.max(-75, Math.min(75, d.ry + dx * 0.5)) });
  };
  const up = () => { setTimeout(() => { drag.current = null; }, 0); };
  const wasDragged = () => !!drag.current?.moved;

  const rows = Math.ceil(doors.length / COLS);
  const cellW = (W - PANEL) / COLS, cellH = H / rows;
  const face = (extra: React.CSSProperties): React.CSSProperties => ({ position: 'absolute', background: shell, border: '2px solid #0b1410', borderRadius: 10, ...extra });

  return (
    <div ref={wrap} className="relative w-full select-none overflow-hidden" style={{ height: (H + 150) * scale, touchAction: 'pan-y' }}>
      <button type="button" className="absolute right-0 top-0 z-10 inline-flex items-center gap-1 rounded-lg bg-white/10 px-2 py-1 text-[11px] text-white hover:bg-white/20" onClick={() => setRot({ x: -14, y: -26 })}>
        <RotateCcw className="h-3 w-3" /> Vue initiale
      </button>
      <p className="absolute left-0 top-0 text-[11px] text-ink-300">Faites glisser pour tourner le coffre</p>
      <div
        onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerLeave={up}
        style={{ position: 'absolute', left: '50%', top: '50%', width: W, height: H, marginLeft: -W / 2, marginTop: -H / 2, perspective: 1400, transform: `scale(${scale})`, cursor: 'grab' }}
      >
        <div style={{ width: W, height: H, position: 'relative', transformStyle: 'preserve-3d', transform: `rotateX(${rot.x}deg) rotateY(${rot.y}deg)`, transition: drag.current ? 'none' : 'transform .5s ease' }}>
          {/* Support de moto */}
          <div style={{ position: 'absolute', left: -30, top: (H - D - 40) / 2, width: W + 60, height: D + 40, background: '#0b1410', borderRadius: 18, transform: `rotateX(-90deg) translateZ(${H / 2 + 14}px)`, boxShadow: '0 0 60px 20px rgba(0,0,0,.45)' }} />
          {/* Carcasse */}
          <div style={face({ width: W, height: H, transform: `rotateY(180deg) translateZ(${D / 2}px)` })} />
          <div style={face({ width: D, height: H, left: (W - D) / 2, transform: `rotateY(90deg) translateZ(${W / 2}px)` })}>
            <div className="flex h-full items-center justify-center text-center text-xs font-black tracking-[.3em] text-white/25" style={{ writingMode: 'vertical-rl' }}>SMARTFOOD</div>
          </div>
          <div style={face({ width: D, height: H, left: (W - D) / 2, transform: `rotateY(-90deg) translateZ(${W / 2}px)` })} />
          <div style={face({ width: W, height: D, top: (H - D) / 2, transform: `rotateX(90deg) translateZ(${H / 2}px)`, background: 'linear-gradient(180deg,#31423a,#1b2a23)' })}>
            <div className="flex h-full items-center justify-center gap-3 text-white/70">
              <span className="rounded-md bg-brand-500 px-2 py-0.5 text-xs font-black text-white">S</span>
              <span className="text-sm font-bold tracking-wide">{name}</span>
              <span className="h-2 w-2 rounded-full" style={{ background: online ? '#22c55e' : '#ef4444', boxShadow: `0 0 8px ${online ? '#22c55e' : '#ef4444'}` }} />
            </div>
          </div>
          <div style={face({ width: W, height: D, top: (H - D) / 2, transform: `rotateX(-90deg) translateZ(${H / 2}px)` })} />

          {/* Facade : portes + console */}
          <div style={{ position: 'absolute', width: W, height: H, transformStyle: 'preserve-3d', transform: `translateZ(${D / 2}px)` }}>
            {doors.map((d, i) => {
              const col = i % COLS, row = Math.floor(i / COLS);
              return (
                <div key={d.number} style={{ position: 'absolute', left: col * cellW, top: row * cellH, width: cellW, height: cellH, transformStyle: 'preserve-3d' }}>
                  {/* Cavite */}
                  <div style={{ position: 'absolute', inset: 3, borderRadius: 8, background: 'radial-gradient(circle at 50% 40%,#3a2a1c 0%,#120c07 75%)', boxShadow: 'inset 0 0 26px 10px rgba(0,0,0,.85)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center', paddingBottom: 10 }}>
                    <div style={{ transition: 'opacity .4s, transform .4s', opacity: d.mealPresent ? 1 : 0, transform: d.mealPresent ? 'none' : 'translateY(10px) scale(.8)', fontSize: cellH * 0.42, lineHeight: 1, filter: 'drop-shadow(0 6px 6px rgba(0,0,0,.6))' }}>🍱</div>
                  </div>
                  {/* Porte sur charniere gauche */}
                  <button
                    type="button"
                    aria-label={`Compartiment ${d.number}, ${compartmentStateLabel[d.state]}${d.doorOpen ? ', porte ouverte' : ''}`}
                    onClick={() => { if (!wasDragged()) onSelect?.(d.number); }}
                    style={{
                      position: 'absolute', inset: 3, borderRadius: 8, padding: 8, textAlign: 'left', color: '#fff',
                      background: d.doorOpen ? 'linear-gradient(135deg,#3b4d44,#22322b)' : 'linear-gradient(135deg,#2c3d35,#1a2821)',
                      border: `2px solid ${selected === d.number ? '#ffffff' : d.mine ? '#22c55e' : '#0b1410'}`,
                      boxShadow: d.doorOpen ? '0 0 0 1px rgba(251,138,76,.6), 18px 0 30px rgba(0,0,0,.5)' : 'inset 0 1px 0 rgba(255,255,255,.08)',
                      transformOrigin: 'left center', transform: `translateZ(1px) rotateY(${d.doorOpen ? -112 : 0}deg)`,
                      transition: 'transform .7s cubic-bezier(.3,1.3,.5,1), border-color .2s', cursor: onSelect ? 'pointer' : 'default',
                    }}
                  >
                    <span style={{ opacity: d.doorOpen ? 0 : 1, transition: 'opacity .3s' }}>
                    <span style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <span style={{ fontSize: 26, fontWeight: 900, lineHeight: 1 }}>{d.number}</span>
                      <span style={{ width: 10, height: 10, borderRadius: 99, background: led[d.state], boxShadow: `0 0 10px ${led[d.state]}` }} />
                    </span>
                    <span style={{ position: 'absolute', left: 8, right: 8, bottom: 6, display: 'flex', alignItems: 'center', gap: 4, fontSize: 9.5, color: '#b4c0ba' }}>
                      {d.locked ? <Lock size={11} /> : <LockOpen size={11} color="#fb8a4c" />}
                      <span style={{ overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>{compartmentStateLabel[d.state]}{d.orderId ? ` · n°${d.orderId}` : ''}</span>
                    </span>
                    <span style={{ position: 'absolute', right: 8, top: '44%', width: 5, height: 22, borderRadius: 3, background: '#0b1410' }} />
                    <span style={{ position: 'absolute', left: 8, top: 38, fontSize: 8.5, letterSpacing: 1, color: '#7f9087', textTransform: 'uppercase' }}>{sizeLabel[d.size]}{d.mine ? ' · le vôtre' : ''}</span>
                    </span>
                  </button>
                </div>
              );
            })}
            {/* Console : ecran + clavier */}
            <div style={{ position: 'absolute', left: W - PANEL, top: 0, width: PANEL, height: H, padding: 8, background: 'linear-gradient(160deg,#1b2a23,#0f1a15)', border: '2px solid #0b1410', borderRadius: 10, transform: 'translateZ(1px)', display: 'flex', flexDirection: 'column', gap: 6 }}>
              <div aria-live="polite" style={{ borderRadius: 6, background: '#03170f', padding: '5px 7px', fontFamily: 'ui-monospace,monospace', color: '#6ee7b7', boxShadow: 'inset 0 0 12px rgba(16,185,129,.25)' }}>
                <div style={{ fontSize: 9.5, minHeight: 24, lineHeight: 1.25 }}>{display}</div>
                <div style={{ fontSize: 17, letterSpacing: 5 }}>{code.replace(/\d/g, '•').padEnd(6, '·')}</div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 4, flex: 1 }}>
                {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((k) => (
                  <button key={k} type="button" disabled={!onDigit} onClick={() => { if (!wasDragged()) onDigit?.(k); }} className="rounded-md bg-white/10 text-base font-bold text-white hover:bg-white/25 active:bg-brand-500 disabled:opacity-40">{k}</button>
                ))}
                <button type="button" aria-label="Effacer" disabled={!onErase} onClick={() => { if (!wasDragged()) onErase?.(); }} className="flex items-center justify-center rounded-md bg-white/10 text-white hover:bg-white/25 disabled:opacity-40"><Delete size={16} /></button>
                <button type="button" disabled={!onDigit} onClick={() => { if (!wasDragged()) onDigit?.('0'); }} className="rounded-md bg-white/10 text-base font-bold text-white hover:bg-white/25 active:bg-brand-500 disabled:opacity-40">0</button>
                <button type="button" disabled={!onSubmit} onClick={() => { if (!wasDragged()) onSubmit?.(); }} className="rounded-md bg-brand-500 text-xs font-black text-white hover:bg-brand-600 disabled:opacity-40">OK</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
