import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banknote, MapPin, Navigation, Phone, Radio } from 'lucide-react';
import { errorMessage, get, post, put } from '../../api/client';
import { dateTime, deliveryStatusLabel, fcfa, orderStatusLabel, paymentMethodLabel } from '../../api/labels';
import type { AppConfig, BoxView, LocationPoint, Mission } from '../../api/types';
import { BoxSimulator } from '../../components/BoxSimulator';
import { MapView, type MapPoint } from '../../components/MapView';
import { Badge, Empty, ErrorBox, PageTitle, Spinner, Stat } from '../../components/ui';
import { toast } from '../../stores/toast';

interface CourierState { available: boolean; vehicle: string; box: BoxView | null }
const ACTIVE = ['OFFERED', 'ACCEPTED', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED'];
const TRACKED = ['ACCEPTED', 'PICKED_UP', 'IN_TRANSIT', 'ARRIVED'];

/**
 * Transmission de la position. Mode reel : GPS du telephone (avec autorisation), une position toutes les 5 secondes au plus.
 * Mode simulation (tests) : le livreur est deplace a la main sur la carte ; chaque position est marquee comme simulee.
 */
function GpsPanel({ missions, simulatorEnabled }: { missions: Mission[]; simulatorEnabled: boolean }) {
  const [mode, setMode] = useState<'off' | 'real' | 'sim'>('off');
  const [last, setLast] = useState<LocationPoint | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const lastSent = useRef(0);
  const tracked = missions.filter((m) => TRACKED.includes(m.status));
  const ids = tracked.map((m) => m.id).join(',');

  const send = async (latitude: number, longitude: number, accuracyM: number | null, simulated: boolean) => {
    try {
      let point: LocationPoint | null = null;
      for (const id of ids.split(',').filter(Boolean)) {
        point = await post<LocationPoint>(`/courier/missions/${id}/location`, { latitude, longitude, accuracyM, simulated });
      }
      if (point) { setLast(point); setProblem(null); }
    } catch (e) { setProblem(errorMessage(e)); }
  };

  useEffect(() => {
    if (mode !== 'real' || !ids) return;
    if (!navigator.geolocation) { setProblem("La géolocalisation n'est pas disponible sur cet appareil."); setMode('off'); return; }
    const watch = navigator.geolocation.watchPosition(
      (p) => {
        if (Date.now() - lastSent.current < 5000) return;
        lastSent.current = Date.now();
        void send(p.coords.latitude, p.coords.longitude, p.coords.accuracy, false);
      },
      (err) => { setProblem(err.code === err.PERMISSION_DENIED ? 'Autorisation de localisation refusée. Activez-la dans les réglages du navigateur.' : 'Position GPS indisponible pour le moment.'); },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 },
    );
    return () => navigator.geolocation.clearWatch(watch);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, ids]);

  useEffect(() => { if (!ids) setMode('off'); }, [ids]);

  if (tracked.length === 0) return <p className="text-sm text-ink-500">Le GPS s'active dès qu'une mission est acceptée.</p>;
  const target = tracked[0];
  const from = last ?? (target.stops[0] ? { latitude: target.stops[0].latitude, longitude: target.stops[0].longitude } : { latitude: target.destLat, longitude: target.destLng });
  const points: MapPoint[] = [
    ...tracked.map((m) => ({ lat: m.destLat, lng: m.destLng, label: `Client : ${m.destinationAddress}`, kind: 'destination' as const })),
    ...tracked.flatMap((m) => m.stops.map((s) => ({ lat: s.latitude, lng: s.longitude, label: `Collecte : ${s.restaurantName}`, kind: 'pickup' as const }))),
  ];
  if (last) points.push({ lat: last.latitude, lng: last.longitude, label: last.simulated ? 'Ma position (simulée)' : 'Ma position', kind: 'courier' });
  const stepToward = () => {
    const dLat = target.destLat - from.latitude, dLng = target.destLng - from.longitude;
    const far = Math.hypot(dLat, dLng) > 0.0015;
    void send(far ? from.latitude + dLat * 0.25 : target.destLat, far ? from.longitude + dLng * 0.25 : target.destLng, 10, true);
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <button className={mode === 'real' ? 'btn-primary btn-sm' : 'btn-ghost btn-sm'} onClick={() => setMode(mode === 'real' ? 'off' : 'real')}><Radio className="h-3.5 w-3.5" /> {mode === 'real' ? 'GPS activé — arrêter' : 'Activer mon GPS'}</button>
        {simulatorEnabled && <button className={mode === 'sim' ? 'btn-dark btn-sm' : 'btn-ghost btn-sm'} onClick={() => setMode(mode === 'sim' ? 'off' : 'sim')}>{mode === 'sim' ? 'Quitter la simulation' : 'Mode simulation GPS (tests)'}</button>}
        {mode === 'sim' && <button className="btn-ghost btn-sm" onClick={stepToward}><Navigation className="h-3.5 w-3.5" /> Avancer vers le client</button>}
        {mode === 'sim' && <span className="rounded bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">SIMULATION — positions fictives</span>}
      </div>
      {problem && <p className="text-sm text-red-600" role="alert">{problem}</p>}
      <MapView points={points} onPick={mode === 'sim' ? (lat, lng) => void send(lat, lng, 10, true) : undefined} />
      <p className="text-xs text-ink-500">
        {mode === 'off' && 'Position non transmise. Le client ne voit pas votre déplacement.'}
        {mode === 'real' && (last ? `Position transmise à ${new Date(last.recordedAt).toLocaleTimeString('fr-FR')}${last.accuracyM ? ` (± ${Math.round(last.accuracyM)} m)` : ''}. Gardez cette page ouverte pendant la livraison.` : 'En attente du premier relevé GPS…')}
        {mode === 'sim' && 'Touchez la carte pour placer le livreur, ou avancez pas à pas vers le client.'}
      </p>
    </div>
  );
}

export function CourierPage() {
  const queryClient = useQueryClient();
  const state = useQuery({ queryKey: ['courier', 'state'], queryFn: () => get<CourierState>('/courier/state'), refetchInterval: 20000 });
  const missions = useQuery({ queryKey: ['courier', 'missions'], queryFn: () => get<Mission[]>('/courier/missions'), refetchInterval: 15000 });
  const config = useQuery({ queryKey: ['config'], queryFn: () => get<AppConfig>('/config') });
  const refresh = () => { queryClient.invalidateQueries({ queryKey: ['courier'] }); queryClient.invalidateQueries({ queryKey: ['boxes'] }); };
  const fail = (e: unknown) => { toast.error(errorMessage(e)); refresh(); };
  const availability = useMutation({ mutationFn: (available: boolean) => put('/courier/availability', { available }), onSuccess: refresh, onError: fail });
  const act = useMutation({ mutationFn: ({ id, action }: { id: number; action: string }) => post(`/courier/missions/${id}/${action}`), onSuccess: refresh, onError: fail });

  if (state.isLoading || missions.isLoading) return <Spinner />;
  if (state.isError || !state.data) return <ErrorBox error={state.error} retry={() => state.refetch()} />;
  if (missions.isError) return <ErrorBox error={missions.error} retry={() => missions.refetch()} />;
  const active = (missions.data ?? []).filter((m) => ACTIVE.includes(m.status));
  const s = state.data;

  return (
    <div className="space-y-4">
      <PageTitle title="Mes missions" subtitle={s.box ? `Vous transportez le ${s.box.name}.` : "Aucun coffre ne vous est attribué. Contactez l'administrateur."}
        action={<button className={s.available ? 'btn-primary' : 'btn-ghost'} disabled={availability.isPending} onClick={() => availability.mutate(!s.available)}>{s.available ? 'Disponible — passer indisponible' : 'Indisponible — me rendre disponible'}</button>} />

      {active.length === 0 && <Empty title="Aucune mission en cours" hint={s.available ? 'Les missions arrivent ici dès qu’un restaurant réserve un compartiment de votre coffre.' : 'Rendez-vous disponible pour recevoir des missions.'} />}
      <div className="grid gap-3 md:grid-cols-2">
        {active.map((m) => (
          <div key={m.id} className="card space-y-2">
            <div className="flex items-center justify-between gap-2"><p className="font-bold">Mission n°{m.id}</p><Badge tone="orange">{deliveryStatusLabel[m.status]}</Badge></div>
            <div className="space-y-1 text-sm">
              {m.stops.map((st) => <p key={st.orderId} className="flex items-start gap-1.5"><MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-leaf-500" /><span><span className="font-semibold">{st.restaurantName}</span> — {st.restaurantAddress} · compartiment {st.compartmentNumber ?? '—'} · {orderStatusLabel[st.orderStatus]}</span></p>)}
              <p className="flex items-start gap-1.5"><MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-ink-900" /><span><span className="font-semibold">{m.customerName}</span> — {m.destinationAddress}</span></p>
              <a className="inline-flex items-center gap-1 text-brand-600" href={`tel:${m.customerPhone}`}><Phone className="h-3.5 w-3.5" /> {m.customerPhone}</a>
            </div>
            <p className="text-xs text-ink-500">{paymentMethodLabel[m.paymentMethod]} · gain estimé {fcfa(m.earning)}</p>
            {m.amountToCollect > 0 && <p className="flex items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-sm font-semibold text-amber-900"><Banknote className="h-4 w-4" /> À encaisser auprès du client : {fcfa(m.amountToCollect)}</p>}
            <div className="flex flex-wrap gap-2">
              {m.status === 'OFFERED' && <><button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: m.id, action: 'accept' })}>Accepter</button><button className="btn-danger btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: m.id, action: 'reject' })}>Refuser</button></>}
              {m.status === 'ACCEPTED' && <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: m.id, action: 'pickup' })}>Confirmer la prise en charge du coffre</button>}
              {m.status === 'PICKED_UP' && <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: m.id, action: 'depart' })}>Confirmer mon départ</button>}
              {m.status === 'IN_TRANSIT' && <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: m.id, action: 'arrive' })}>Je suis arrivé</button>}
              {['PICKED_UP', 'IN_TRANSIT', 'ARRIVED'].includes(m.status) && m.amountToCollect > 0 && <button className="btn-dark btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: m.id, action: 'cash-received' })}>Espèces reçues</button>}
              <a className="btn-ghost btn-sm" target="_blank" rel="noreferrer" href={`https://www.openstreetmap.org/directions?to=${m.destLat},${m.destLng}`}><Navigation className="h-3.5 w-3.5" /> Itinéraire</a>
            </div>
            {m.status === 'ARRIVED' && <p className="text-sm text-ink-500">Présentez le coffre au client. La mission se termine quand il a retiré son repas et refermé la porte.</p>}
          </div>
        ))}
      </div>

      <div className="card space-y-3"><h2>Position GPS</h2><GpsPanel missions={active} simulatorEnabled={config.data?.simulatorEnabled ?? false} /></div>
      {s.box && <div className="space-y-2"><h2>Mon coffre</h2><BoxSimulator boxId={s.box.id} readOnly /></div>}
    </div>
  );
}

export function CourierHistoryPage() {
  const missions = useQuery({ queryKey: ['courier', 'missions'], queryFn: () => get<Mission[]>('/courier/missions') });
  const earnings = useQuery({ queryKey: ['courier', 'earnings'], queryFn: () => get<{ completedDeliveries: number; total: number; today: number }>('/courier/earnings') });
  if (missions.isLoading || earnings.isLoading) return <Spinner />;
  if (missions.isError) return <ErrorBox error={missions.error} retry={() => missions.refetch()} />;
  const done = (missions.data ?? []).filter((m) => !ACTIVE.includes(m.status));
  return (
    <div className="space-y-4">
      <PageTitle title="Historique et revenus" />
      {earnings.data && <div className="grid grid-cols-3 gap-3"><Stat label="Aujourd'hui" value={fcfa(earnings.data.today)} /><Stat label="Total" value={fcfa(earnings.data.total)} /><Stat label="Livraisons terminées" value={earnings.data.completedDeliveries} /></div>}
      {done.length === 0 ? <Empty title="Aucune livraison terminée" /> : (
        <div className="card overflow-x-auto"><table className="table"><thead><tr><th>Mission</th><th>Date</th><th>Destination</th><th>Statut</th><th>Gain</th></tr></thead>
          <tbody>{done.map((m) => <tr key={m.id}><td>n°{m.id}</td><td>{dateTime(m.completedAt ?? m.createdAt)}</td><td>{m.destinationAddress}</td><td>{deliveryStatusLabel[m.status]}</td><td>{m.status === 'COMPLETED' ? fcfa(m.earning) : '—'}</td></tr>)}</tbody></table></div>
      )}
    </div>
  );
}
