import type { ReactNode } from 'react';
import { AlertTriangle, Loader2, X } from 'lucide-react';
import { useToasts } from '../stores/toast';
import { errorMessage } from '../api/client';

export function Spinner({ label = 'Chargement…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-ink-500" role="status">
      <Loader2 className="h-4 w-4 animate-spin" /> {label}
    </div>
  );
}

export function ErrorBox({ error, retry }: { error: unknown; retry?: () => void }) {
  return (
    <div className="card flex items-start gap-3 border-red-200 bg-red-50 text-sm text-red-800" role="alert">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="flex-1">{errorMessage(error)}</div>
      {retry && <button className="btn-ghost btn-sm" onClick={retry}>Réessayer</button>}
    </div>
  );
}

export function Empty({ title, hint, action }: { title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="card flex flex-col items-center gap-2 py-10 text-center">
      <p className="font-semibold">{title}</p>
      {hint && <p className="max-w-sm text-sm text-ink-500">{hint}</p>}
      {action}
    </div>
  );
}

const tones = {
  gray: 'bg-ink-100 text-ink-700', green: 'bg-leaf-100 text-leaf-700', orange: 'bg-brand-100 text-brand-700',
  red: 'bg-red-100 text-red-700', blue: 'bg-sky-100 text-sky-800', dark: 'bg-ink-900 text-white',
};
export type Tone = keyof typeof tones;
export function Badge({ tone = 'gray', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>{children}</span>;
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="label">{label}</span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}

export function PageTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-ink-500">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="card">
      <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">{label}</p>
      <p className="mt-1 text-2xl font-bold">{value}</p>
      {hint && <p className="text-xs text-ink-500">{hint}</p>}
    </div>
  );
}

/** Histogramme simple : une barre par jour, valeur au-dessus. */
export function BarChart({ data, format }: { data: { label: string; value: number }[]; format: (n: number) => string }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div className="flex h-40 items-end gap-2" role="img" aria-label="Histogramme">
      {data.map((d) => (
        <div key={d.label} className="flex flex-1 flex-col items-center gap-1" title={`${d.label} : ${format(d.value)}`}>
          <span className="text-[10px] text-ink-500">{d.value > 0 ? format(d.value) : ''}</span>
          <div className="w-full rounded-t-md bg-brand-500 transition-all" style={{ height: `${Math.max(2, (d.value / max) * 110)}px`, opacity: d.value > 0 ? 1 : 0.2 }} />
          <span className="text-[10px] font-medium text-ink-500">{d.label}</span>
        </div>
      ))}
    </div>
  );
}

export function Toasts() {
  const { toasts, dismiss } = useToasts();
  const color = { success: 'border-leaf-500 bg-leaf-100 text-leaf-700', error: 'border-red-300 bg-red-50 text-red-800', info: 'border-ink-100 bg-white text-ink-900' };
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-[2000] flex flex-col items-center gap-2 px-4" aria-live="polite">
      {toasts.map((t) => (
        <div key={t.id} className={`rise pointer-events-auto flex w-full max-w-md items-start gap-2 rounded-xl border px-4 py-3 text-sm shadow-soft ${color[t.kind]}`}>
          <span className="flex-1">{t.text}</span>
          <button onClick={() => dismiss(t.id)} aria-label="Fermer"><X className="h-4 w-4" /></button>
        </div>
      ))}
    </div>
  );
}
