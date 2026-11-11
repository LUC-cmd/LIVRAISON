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

/** En-tete de page : bandeau sombre, pastille d'icone, titre, sous-titre et actions. */
export function PageTitle({ title, subtitle, action, icon }: { title: string; subtitle?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="relative mb-5 overflow-hidden rounded-3xl bg-gradient-to-r from-brand-600 via-brand-500 to-amber-400 px-5 py-5 text-white shadow-soft sm:px-7 sm:py-6">
      <div className="pointer-events-none absolute -right-8 -top-14 h-44 w-44 rounded-full bg-white/20" />
      <div className="pointer-events-none absolute -bottom-16 right-28 h-36 w-36 rounded-full bg-white/10" />
      <div className="pointer-events-none absolute -bottom-10 -left-10 h-28 w-28 rounded-full bg-black/10" />
      <div className="relative flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          {icon && <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-white text-brand-600 shadow-soft">{icon}</span>}
          <div>
            <h1 className="text-white drop-shadow-sm">{title}</h1>
            {subtitle && <p className="mt-1 max-w-2xl text-sm font-medium text-white/90">{subtitle}</p>}
          </div>
        </div>
        {action && <div className="flex flex-wrap items-center gap-2">{action}</div>}
      </div>
    </div>
  );
}

const statTones = { orange: 'bg-brand-100 text-brand-600', green: 'bg-leaf-100 text-leaf-700', dark: 'bg-ink-900 text-white', blue: 'bg-sky-100 text-sky-700', red: 'bg-red-100 text-red-700' };
export function Stat({ label, value, hint, icon, tone = 'orange' }: { label: string; value: ReactNode; hint?: string; icon?: ReactNode; tone?: keyof typeof statTones }) {
  return (
    <div className="card flex items-start gap-3">
      {icon && <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${statTones[tone]}`}>{icon}</span>}
      <div className="min-w-0">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-ink-500">{label}</p>
        <p className="mt-0.5 truncate text-2xl font-extrabold tracking-tight">{value}</p>
        {hint && <p className="text-xs text-ink-500">{hint}</p>}
      </div>
    </div>
  );
}

export function Section({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <h2 className="flex items-center gap-2"><span className="h-5 w-1.5 rounded-full bg-brand-500" />{title}</h2>
        {action}
      </div>
      {children}
    </section>
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
