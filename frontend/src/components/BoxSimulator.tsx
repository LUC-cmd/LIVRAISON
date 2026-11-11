import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BatteryLow, BatteryMedium, Delete, DoorClosed, DoorOpen, Lock, LockOpen, Thermometer, UtensilsCrossed, Wifi, WifiOff, Wrench } from 'lucide-react';
import { errorMessage, get, post, put } from '../api/client';
import { compartmentStateLabel, faultLabel, sizeLabel } from '../api/labels';
import type { BoxView, CompartmentState, CompartmentView, Hardware, HwFault } from '../api/types';
import { toast } from '../stores/toast';
import { Badge, ErrorBox, Spinner, type Tone } from './ui';

const stateTone: Record<CompartmentState, Tone> = {
  AVAILABLE: 'green', RESERVED: 'blue', LOADING: 'orange', OCCUPIED: 'orange', LOCKED: 'dark', WITHDRAWING: 'orange', OPEN: 'orange', FAULT: 'red',
};
const sizeSpan = { SMALL: 'min-h-[92px]', MEDIUM: 'min-h-[112px]', LARGE: 'min-h-[132px]' };

interface Props {
  boxId: number;
  /** Clavier central : visible pour celui qui retire un repas. */
  keypad?: boolean;
  /** Pannes et controles reserves a l'administrateur. */
  admin?: boolean;
  /** Le livreur voit l'etat mais ne peut ni saisir de code ni manipuler les portes. */
  readOnly?: boolean;
}

/**
 * Coffre virtuel a compartiments. L'etat affiche vient du serveur (etat metier) et du materiel simule (porte, serrure, repas).
 * Chaque bouton envoie une action physique au simulateur ; c'est le serveur qui decide de toute ouverture.
 */
export function BoxSimulator({ boxId, keypad = false, admin = false, readOnly = false }: Props) {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [selected, setSelected] = useState<number | null>(null);

  const box = useQuery({ queryKey: ['boxes', boxId], queryFn: () => get<BoxView>(`/boxes/${boxId}`), refetchInterval: 8000 });
  const simulated = box.data?.device?.kind === 'SIMULATED';
  const hw = useQuery({ queryKey: ['hardware', boxId], queryFn: () => get<Hardware>(`/simulator/boxes/${boxId}`), enabled: simulated, refetchInterval: 8000 });

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['boxes'] });
    queryClient.invalidateQueries({ queryKey: ['hardware'] });
    queryClient.invalidateQueries({ queryKey: ['orders'] });
    queryClient.invalidateQueries({ queryKey: ['courier'] });
    queryClient.invalidateQueries({ queryKey: ['admin'] });
  };
  const action = useMutation({
    mutationFn: (run: () => Promise<unknown>) => run(),
    onSuccess: refresh,
    onError: (e) => { toast.error(errorMessage(e)); refresh(); },
  });
  const run = (fn: () => Promise<unknown>) => action.mutate(fn);

  if (box.isLoading) return <Spinner label="Connexion au coffre…" />;
  if (box.isError || !box.data) return <ErrorBox error={box.error} retry={() => box.refetch()} />;
  const b = box.data;
  const device = b.device;
  const hardware = hw.data;
  const hwOf = (n: number) => hardware?.compartments.find((c) => c.number === n);
  const keypadLocked = !!b.keypadLockedUntil && new Date(b.keypadLockedUntil) > new Date();
  const picked: CompartmentView | undefined = b.compartments.find((c) => c.number === selected);

  const press = (digit: string) => setCode((c) => (c.length < 6 ? c + digit : c));
  const submit = () => {
    if (code.length !== 6) { toast.error('Le code contient six chiffres.'); return; }
    run(() => post(`/simulator/boxes/${boxId}/keypad`, { code }));
    setCode('');
  };

  return (
    <div className="rounded-3xl bg-ink-900 p-4 text-white shadow-soft">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="text-xs uppercase tracking-widest text-ink-300">{b.serial}{simulated && ' · coffre simulé'}</p>
          <p className="text-lg font-bold">{b.name}</p>
        </div>
        {device && (
          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 ${device.online ? 'bg-leaf-500/20 text-leaf-100' : 'bg-red-500/30 text-red-100'}`}>
              {device.online ? <Wifi className="h-3.5 w-3.5" /> : <WifiOff className="h-3.5 w-3.5" />} {device.online ? 'En ligne' : 'Hors connexion'}
            </span>
            <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 ${device.batteryPct < 15 ? 'bg-red-500/30 text-red-100' : 'bg-white/10'}`}>
              {device.batteryPct < 15 ? <BatteryLow className="h-3.5 w-3.5" /> : <BatteryMedium className="h-3.5 w-3.5" />} {device.batteryPct} %
            </span>
            <span className="inline-flex items-center gap-1 rounded-full bg-white/10 px-2 py-1"><Thermometer className="h-3.5 w-3.5" /> {device.temperatureC} °C</span>
          </div>
        )}
      </div>

      <div className="grid gap-4 md:grid-cols-[1fr_auto]">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {b.compartments.map((c) => {
            const h = hwOf(c.number);
            const open = h ? h.doorOpen : c.doorOpen;
            return (
              <button
                key={c.id}
                onClick={() => setSelected(c.number === selected ? null : c.number)}
                className={`relative flex flex-col justify-between rounded-2xl border-2 p-3 text-left transition ${sizeSpan[c.size]} ${open ? 'border-brand-400 bg-brand-500/15' : 'border-white/10 bg-white/5'} ${selected === c.number ? 'ring-2 ring-white' : ''} ${c.mine ? 'outline outline-2 outline-offset-2 outline-leaf-500' : ''}`}
                aria-label={`Compartiment ${c.number}, ${compartmentStateLabel[c.state]}`}
              >
                <div className="flex items-start justify-between">
                  <span className="text-2xl font-black leading-none">{c.number}</span>
                  <span className="text-[10px] uppercase text-ink-300">{sizeLabel[c.size]}</span>
                </div>
                <div className="flex items-center gap-1.5 text-ink-300">
                  {open ? <DoorOpen className="h-4 w-4 text-brand-400" /> : <DoorClosed className="h-4 w-4" />}
                  {(h ? h.latchEngaged : c.locked) ? <Lock className="h-4 w-4" /> : <LockOpen className="h-4 w-4 text-brand-400" />}
                  {h?.mealPresent && <UtensilsCrossed className="h-4 w-4 text-leaf-100" />}
                  {h && h.fault !== 'NONE' && <Wrench className="h-4 w-4 text-red-300" />}
                </div>
                <div className="flex flex-wrap items-center gap-1">
                  <Badge tone={stateTone[c.state]}>{compartmentStateLabel[c.state]}</Badge>
                  {c.orderId && <span className="text-[10px] text-ink-300">Cmd n°{c.orderId}</span>}
                  {c.mine && <span className="text-[10px] font-semibold text-leaf-100">le vôtre</span>}
                </div>
              </button>
            );
          })}
        </div>

        <div className="w-full md:w-56">
          <div className="rounded-xl bg-emerald-950 px-3 py-2 font-mono text-emerald-300" aria-live="polite">
            <p className="text-[10px] uppercase text-emerald-600">Écran</p>
            <p className="min-h-[20px] text-sm">{(hardware?.displayText ?? device?.displayText) || (keypadLocked ? 'CLAVIER BLOQUE' : 'SAISISSEZ VOTRE CODE')}</p>
            <p className="mt-1 text-xl tracking-[.4em]">{code.padEnd(6, '·').replace(/\d/g, '•')}</p>
          </div>
          {keypad && !readOnly && simulated && (
            <div className="mt-2 grid grid-cols-3 gap-1.5">
              {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
                <button key={d} className="rounded-lg bg-white/10 py-2.5 text-lg font-bold hover:bg-white/20" onClick={() => press(d)}>{d}</button>
              ))}
              <button className="flex items-center justify-center rounded-lg bg-white/10 py-2.5 hover:bg-white/20" onClick={() => setCode((c) => c.slice(0, -1))} aria-label="Effacer"><Delete className="h-5 w-5" /></button>
              <button className="rounded-lg bg-white/10 py-2.5 text-lg font-bold hover:bg-white/20" onClick={() => press('0')}>0</button>
              <button className="rounded-lg bg-brand-500 py-2.5 text-sm font-bold hover:bg-brand-600 disabled:opacity-50" disabled={action.isPending} onClick={submit}>OK</button>
            </div>
          )}
          {readOnly && <p className="mt-2 text-xs text-ink-300">Vous transportez ce coffre. Seuls les clients ouvrent leur compartiment, avec leur code.</p>}
        </div>
      </div>

      {picked && simulated && !readOnly && (
        <div className="rise mt-3 rounded-2xl bg-white/5 p-3">
          <p className="mb-2 text-sm font-semibold">Compartiment {picked.number} — actions physiques (simulation)</p>
          {picked.faultReason && <p className="mb-2 text-xs text-red-200">Panne : {picked.faultReason}</p>}
          <div className="flex flex-wrap gap-2">
            <button className="btn-ghost btn-sm" disabled={action.isPending || !hwOf(picked.number)?.doorOpen || hwOf(picked.number)?.mealPresent} onClick={() => run(() => post(`/simulator/boxes/${boxId}/compartments/${picked.number}/meal`, { present: true }))}>Déposer le repas</button>
            <button className="btn-ghost btn-sm" disabled={action.isPending || !hwOf(picked.number)?.doorOpen || !hwOf(picked.number)?.mealPresent} onClick={() => run(() => post(`/simulator/boxes/${boxId}/compartments/${picked.number}/meal`, { present: false }))}>Retirer le repas</button>
            <button className="btn-primary btn-sm" disabled={action.isPending || !hwOf(picked.number)?.doorOpen} onClick={() => run(() => post(`/simulator/boxes/${boxId}/compartments/${picked.number}/close`))}>Fermer la porte</button>
          </div>
          {!hwOf(picked.number)?.doorOpen && <p className="mt-2 text-xs text-ink-300">Porte fermée et verrouillée. Elle ne s'ouvre que sur autorisation du serveur.</p>}
          {admin && (
            <div className="mt-3 border-t border-white/10 pt-3">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-300">Pannes du compartiment</p>
              <div className="flex flex-wrap items-center gap-2">
                <select className="input max-w-xs !py-1.5 text-ink-900" value={hwOf(picked.number)?.fault ?? 'NONE'} onChange={(e) => run(() => put(`/simulator/admin/boxes/${boxId}/compartments/${picked.number}/fault`, { fault: e.target.value as HwFault }))}>
                  {(Object.keys(faultLabel) as HwFault[]).map((f) => <option key={f} value={f}>{faultLabel[f]}</option>)}
                </select>
                <button className="btn-danger btn-sm" disabled={action.isPending || hwOf(picked.number)?.doorOpen} onClick={() => run(() => post(`/simulator/admin/boxes/${boxId}/compartments/${picked.number}/force-open`))}>Forcer la porte (effraction)</button>
                {picked.state === 'FAULT' && (
                  <>
                    <button className="btn-ghost btn-sm" disabled={action.isPending} onClick={() => run(() => post(`/admin/compartments/${picked.id}/reset`, { mealDelivered: true }))}>Remettre en service (repas remis)</button>
                    <button className="btn-ghost btn-sm" disabled={action.isPending} onClick={() => run(() => post(`/admin/compartments/${picked.id}/reset`, { mealDelivered: false }))}>Remettre en service (repas non remis)</button>
                  </>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {admin && simulated && hardware && (
        <div className="mt-3 rounded-2xl bg-white/5 p-3">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-ink-300">Pannes du coffre (simulation)</p>
          <div className="flex flex-wrap gap-2">
            <button className="btn-ghost btn-sm" disabled={action.isPending} onClick={() => run(() => put(`/simulator/admin/boxes/${boxId}/device`, { online: !hardware.online }))}>{hardware.online ? 'Couper la connexion' : 'Rétablir la connexion'}</button>
            <button className="btn-ghost btn-sm" disabled={action.isPending} onClick={() => run(() => put(`/simulator/admin/boxes/${boxId}/device`, { batteryPct: hardware.batteryPct < 15 ? 100 : 8 }))}>{hardware.batteryPct < 15 ? 'Recharger la batterie' : 'Batterie faible (8 %)'}</button>
            <button className="btn-ghost btn-sm" disabled={action.isPending || hardware.dropNextCommand} onClick={() => run(() => put(`/simulator/admin/boxes/${boxId}/device`, { dropNextCommand: true }))}>{hardware.dropNextCommand ? 'Prochaine commande sera perdue' : "Perdre la prochaine commande d'ouverture"}</button>
            <button className="btn-ghost btn-sm" disabled={action.isPending} onClick={() => run(async () => { const r = await post<{ incidents: number }>('/simulator/admin/watchdog'); toast.info(`Surveillance des capteurs : ${r.incidents} incident(s) détecté(s).`); })}>Lancer la surveillance des capteurs</button>
            {keypadLocked && <button className="btn-ghost btn-sm" disabled={action.isPending} onClick={() => run(() => post(`/admin/boxes/${boxId}/unlock-keypad`))}>Débloquer le clavier</button>}
          </div>
        </div>
      )}
      {!simulated && <p className="mt-3 text-xs text-ink-300">Coffre physique : les portes et le clavier se manipulent sur l'appareil.</p>}
    </div>
  );
}
