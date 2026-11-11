import { useCallback, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { Bike, Check, ChefHat, KeyRound, Play, Smartphone } from 'lucide-react';
import { errorMessage, get } from '../api/client';
import { compartmentStateLabel, deliveryStatusLabel, fcfa, orderStatusLabel } from '../api/labels';
import type { Address, AppConfig, AuthResponse, BoxView, CodeView, GroupView, Hardware, Mission, OrderView, RestaurantDetail, RestaurantProfile } from '../api/types';
import { Box3D } from '../components/Box3D';
import { Badge, Empty, PageTitle, Spinner } from '../components/ui';

/** Comptes de demonstration crees par le serveur quand DEMO_DATA=true. Ils ne servent qu'a ce prototype. */
const DEMO = { client: 'kossi@demo.tg', restaurant: 'afi@demo.tg', courier: 'koffi@demo.tg', password: 'Demo1234!' };

/** Message d'une erreur : celui du serveur pour un appel, sinon celui de l'etape. */
const readable = (e: unknown) => (e instanceof Error && !axios.isAxiosError(e) ? e.message : errorMessage(e));

type Actor = 'client' | 'restaurant' | 'courier';
interface Snapshot { order: OrderView | null; group: GroupView | null; box: BoxView | null; hw: Hardware | null; mission: Mission | null; code: string | null }

const steps = [
  { who: 'client', title: 'Le client commande', detail: 'Kossi choisit un plat chez « Chez Afi » et valide son panier.' },
  { who: 'restaurant', title: 'Le restaurant accepte et prépare', detail: 'Le repas est prêt : le serveur réserve un compartiment libre.' },
  { who: 'restaurant', title: 'Le restaurant ouvre son compartiment', detail: 'Autorisation temporaire : seule la porte attribuée se déverrouille.' },
  { who: 'restaurant', title: 'Le repas est déposé', detail: 'Le restaurant place le repas dans le compartiment ouvert.' },
  { who: 'restaurant', title: 'La porte est refermée', detail: 'Le capteur détecte la fermeture, la serrure est vérifiée, puis le code est envoyé au client.' },
  { who: 'courier', title: 'Le livreur prend le coffre et part', detail: 'Mission acceptée, coffre pris en charge, départ. Sa position est transmise.' },
  { who: 'courier', title: 'Le livreur arrive chez le client', detail: 'Arrivée signalée et paiement à la livraison encaissé.' },
  { who: 'client', title: 'Le client tape son code', detail: 'Tapez le code affiché sur le téléphone du client, puis OK. Le serveur ouvre uniquement son compartiment.' },
  { who: 'client', title: 'Le client retire son repas', detail: 'Le compartiment est ouvert : le repas est récupéré.' },
  { who: 'client', title: 'La porte est refermée', detail: 'Retrait enregistré, code invalidé, compartiment libéré après vérification.' },
] as const;
const whoIcon = { client: Smartphone, restaurant: ChefHat, courier: Bike };
const whoLabel = { client: 'Client', restaurant: 'Restaurant', courier: 'Livreur' };

/** Etape en cours, deduite de l'etat reel du serveur : la demonstration reprend ou elle s'etait arretee. */
function currentStep(s: Snapshot): number {
  const o = s.order;
  if (!o || ['COLLECTED', 'REJECTED', 'CANCELLED', 'AWAITING_PAYMENT'].includes(o.status)) return 0;
  if (['PENDING', 'ACCEPTED', 'READY'].includes(o.status)) return 1;
  const hw = s.hw?.compartments.find((c) => c.number === o.compartmentNumber);
  if (o.status === 'RESERVED') return o.compartmentState === 'LOADING' ? (hw?.mealPresent ? 4 : 3) : 2;
  if (o.status === 'LOADED') return 5;
  if (o.status === 'IN_TRANSIT') return 6;
  if (o.compartmentState === 'OPEN') return hw?.mealPresent ? 8 : 9;
  if (s.group?.payment?.status === 'PENDING') return 6;
  return 7;
}

export function SimulationPage() {
  const config = useQuery({ queryKey: ['config'], queryFn: () => get<AppConfig>('/config') });
  const tokens = useRef<Record<Actor, string> | null>(null);
  const [snap, setSnap] = useState<Snapshot>({ order: null, group: null, box: null, hw: null, mission: null, code: null });
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [log, setLog] = useState<string[]>([]);
  const [typed, setTyped] = useState('');
  const [finished, setFinished] = useState(false);
  const say = (line: string) => setLog((l) => [`${new Date().toLocaleTimeString('fr-FR')} — ${line}`, ...l].slice(0, 30));

  const call = useCallback(async <T,>(actor: Actor, method: 'get' | 'post' | 'put', url: string, body?: unknown): Promise<T> => {
    const response = await axios.request<T>({ baseURL: '/api/v1', url, method, data: method === 'get' ? undefined : body ?? {}, headers: { Authorization: `Bearer ${tokens.current![actor]}` } });
    return response.data;
  }, []);

  /** Relit l'etat reel : derniere commande du client, coffre, materiel simule, mission du livreur. */
  const refresh = useCallback(async (keepCode: string | null): Promise<Snapshot> => {
    const groups = await call<GroupView[]>('client', 'get', '/customer/orders');
    const group = groups[0] ?? null;
    const order = group?.orders[0] ?? null;
    const boxes = await call<BoxView[]>('restaurant', 'get', '/boxes');
    const box = boxes.find((b) => b.id === order?.boxId) ?? boxes[0] ?? null;
    const hw = box ? await call<Hardware>('restaurant', 'get', `/simulator/boxes/${box.id}`) : null;
    const missions = order?.deliveryId ? await call<Mission[]>('courier', 'get', '/courier/missions') : [];
    const mission = missions.find((m) => m.id === order?.deliveryId) ?? null;
    let code = keepCode;
    if (order?.codeActive && !code) code = (await call<CodeView>('client', 'get', `/customer/order-items/${order.id}/code`)).code;
    if (!order?.codeActive) code = null;
    const next = { order, group, box, hw, mission, code };
    setSnap(next);
    return next;
  }, [call]);

  // Connexion des trois comptes de demonstration, puis lecture de l'etat.
  useEffect(() => {
    if (!config.data?.simulatorEnabled) return;
    let cancelled = false;
    (async () => {
      try {
        const login = async (email: string) => (await axios.post<AuthResponse>('/api/v1/auth/login', { email, password: DEMO.password })).data.accessToken;
        tokens.current = { client: await login(DEMO.client), restaurant: await login(DEMO.restaurant), courier: await login(DEMO.courier) };
        await call('courier', 'put', '/courier/availability', { available: true });
        await refresh(null);
        if (!cancelled) setReady(true);
      } catch (e) {
        if (!cancelled) setError(`Démonstration indisponible : ${errorMessage(e)} (les comptes de démonstration doivent exister sur le serveur).`);
      }
    })();
    return () => { cancelled = true; };
  }, [config.data?.simulatorEnabled, call, refresh]);

  const step = finished ? steps.length : currentStep(snap);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try { await action(); } catch (e) { setError(readable(e)); }
    try { await refresh(snap.code); } catch { /* l'erreur principale est deja affichee */ }
    setBusy(false);
  };

  const advance = () => run(async () => {
    const { order, box, mission, group } = snap;
    switch (step) {
      case 0: {
        setFinished(false);
        setTyped('');
        const address = (await call<Address[]>('client', 'get', '/me/addresses'))[0];
        const mine = await call<RestaurantProfile>('restaurant', 'get', '/restaurant/profile');
        const menu = await call<RestaurantDetail>('client', 'get', `/restaurants/${mine.restaurant.id}`);
        const dish = [...menu.categories.flatMap((c) => c.dishes), ...menu.uncategorized].find((d) => d.available);
        if (!address || !dish) throw new Error('Adresse ou plat de démonstration introuvable.');
        await call('client', 'post', '/customer/orders', { addressId: address.id, paymentMethod: 'CASH_ON_DELIVERY', items: [{ dishId: dish.id, quantity: 1 }] });
        say(`Client : commande de 1 × ${dish.name} (${fcfa(dish.price)}), paiement à la livraison.`);
        break;
      }
      case 1: {
        if (order!.status === 'PENDING') await call('restaurant', 'post', `/restaurant/orders/${order!.id}/accept`);
        const result = order!.status === 'READY'
          ? await call<{ compartmentReserved: boolean; message: string }>('restaurant', 'post', `/restaurant/orders/${order!.id}/assign-compartment`)
          : await call<{ compartmentReserved: boolean; message: string }>('restaurant', 'post', `/restaurant/orders/${order!.id}/ready`);
        say(`Restaurant : commande acceptée et prête. ${result.message}`);
        if (!result.compartmentReserved) throw new Error(result.message);
        break;
      }
      case 2:
        await call('restaurant', 'post', `/restaurant/orders/${order!.id}/loading-authorization`);
        say(`Serveur : autorisation de chargement, ouverture du compartiment ${order!.compartmentNumber} uniquement.`);
        break;
      case 3:
        await call('restaurant', 'post', `/simulator/boxes/${box!.id}/compartments/${order!.compartmentNumber}/meal`, { present: true });
        say('Restaurant : repas déposé dans le compartiment.');
        break;
      case 4:
        await call('restaurant', 'post', `/simulator/boxes/${box!.id}/compartments/${order!.compartmentNumber}/close`);
        say('Coffre : porte fermée, serrure vérifiée. Serveur : code de retrait généré et envoyé au client.');
        break;
      case 5: {
        let status = mission?.status;
        const id = order!.deliveryId;
        if (status === 'OFFERED') { await call('courier', 'post', `/courier/missions/${id}/accept`); status = 'ACCEPTED'; }
        if (status === 'ACCEPTED') { await call('courier', 'post', `/courier/missions/${id}/pickup`); status = 'PICKED_UP'; }
        if (status === 'PICKED_UP') await call('courier', 'post', `/courier/missions/${id}/depart`);
        const from = mission?.stops[0];
        if (from) await call('courier', 'post', `/courier/missions/${id}/location`, { latitude: from.latitude, longitude: from.longitude, accuracyM: 10, simulated: true });
        say('Livreur : mission acceptée, coffre pris en charge, départ (position simulée transmise).');
        break;
      }
      case 6: {
        const id = order!.deliveryId;
        if (mission?.status === 'IN_TRANSIT') {
          await call('courier', 'post', `/courier/missions/${id}/location`, { latitude: mission.destLat, longitude: mission.destLng, accuracyM: 10, simulated: true });
          await call('courier', 'post', `/courier/missions/${id}/arrive`);
        }
        if (group?.payment?.status === 'PENDING') await call('courier', 'post', `/courier/missions/${id}/cash-received`);
        say('Livreur : arrivé chez le client, espèces reçues.');
        break;
      }
      case 8:
        await call('client', 'post', `/simulator/boxes/${box!.id}/compartments/${order!.compartmentNumber}/meal`, { present: false });
        say('Client : repas retiré du compartiment.');
        break;
      case 9:
        await call('client', 'post', `/simulator/boxes/${box!.id}/compartments/${order!.compartmentNumber}/close`);
        say('Coffre : porte refermée. Serveur : retrait enregistré, code invalidé, compartiment libéré.');
        setFinished(true);
        break;
    }
  });

  /** Etape 8 : le code tape sur le clavier 3D part au serveur, qui decide de l'ouverture. */
  const submitCode = (value: string) => run(async () => {
    if (value.length !== 6) throw new Error('Le code contient six chiffres.');
    const hw = await call<Hardware>('client', 'post', `/simulator/boxes/${snap.box!.id}/keypad`, { code: value });
    setTyped('');
    say(`Clavier : code saisi. Écran du coffre : « ${hw.displayText} ».`);
  });

  if (config.isLoading) return <Spinner />;
  if (config.data && !config.data.simulatorEnabled) return <Empty title="Simulation désactivée" hint="Le coffre virtuel n'est pas activé sur ce serveur." />;
  if (!ready) return error ? <div className="card border-red-200 bg-red-50 text-sm text-red-800" role="alert">{error}</div> : <Spinner label="Préparation de la démonstration…" />;

  const { order, box, hw, mission, code } = snap;
  const atKeypad = step === 7;
  const done = step >= steps.length;

  return (
    <div className="space-y-4">
      <PageTitle title="Simulation 3D du coffre" subtitle="Prototype virtuel : chaque étape appelle le vrai serveur avec les comptes de démonstration. Rien n'est joué « pour de faux » à l'écran." />
      <div className="grid gap-4 lg:grid-cols-[1fr_340px]">
        <div className="space-y-3">
          <div className="rounded-3xl bg-ink-900 p-4 shadow-soft">
            {box ? (
              <Box3D
                name={box.name}
                online={box.device?.online ?? false}
                display={hw?.displayText || (atKeypad ? 'SAISISSEZ VOTRE CODE' : 'SMARTFOOD')}
                code={typed}
                onDigit={atKeypad && !busy ? (d) => setTyped((t) => (t.length < 6 ? t + d : t)) : undefined}
                onErase={atKeypad && !busy ? () => setTyped((t) => t.slice(0, -1)) : undefined}
                onSubmit={atKeypad && !busy ? () => submitCode(typed) : undefined}
                doors={box.compartments.map((c) => {
                  const h = hw?.compartments.find((x) => x.number === c.number);
                  return { number: c.number, size: c.size, state: c.state, doorOpen: h?.doorOpen ?? c.doorOpen, locked: h?.latchEngaged ?? c.locked, mealPresent: h?.mealPresent ?? false, mine: !!order && c.number === order.compartmentNumber && step > 1 && !done, orderId: c.orderId };
                })}
              />
            ) : <p className="py-10 text-center text-sm text-ink-300">Aucun coffre en service.</p>}
          </div>

          <div className="card space-y-3">
            {done ? (
              <>
                <p className="flex items-center gap-2 font-semibold text-leaf-700"><Check className="h-5 w-5" /> Livraison terminée : le client a récupéré uniquement sa commande.</p>
                <button className="btn-primary" disabled={busy} onClick={() => { setFinished(false); setLog([]); }}>Rejouer une nouvelle commande</button>
              </>
            ) : (
              <>
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-ink-500">Étape {step + 1} sur {steps.length} · {whoLabel[steps[step].who]}</p>
                  <p className="text-lg font-bold">{steps[step].title}</p>
                  <p className="text-sm text-ink-500">{steps[step].detail}</p>
                </div>
                {atKeypad ? (
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm">Tapez le code sur le clavier du coffre, ou :</span>
                    <button className="btn-dark btn-sm" disabled={busy || !code} onClick={() => code && submitCode(code)}>Saisir le bon code automatiquement</button>
                    <button className="btn-ghost btn-sm" disabled={busy} onClick={() => submitCode('000000' === code ? '111111' : '000000')}>Essayer un mauvais code</button>
                  </div>
                ) : (
                  <button className="btn-primary" disabled={busy} onClick={advance}><Play className="h-4 w-4" /> {busy ? 'En cours…' : steps[step].title}</button>
                )}
              </>
            )}
            {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{error}</p>}
          </div>
        </div>

        <aside className="space-y-3">
          <div className="card space-y-2">
            <p className="flex items-center gap-2 text-sm font-semibold"><Smartphone className="h-4 w-4 text-brand-500" /> Téléphone du client</p>
            {order && !done ? (
              <>
                <Badge tone="blue">{orderStatusLabel[order.status]}</Badge>
                {code ? (
                  <div className="rounded-xl border-2 border-dashed border-brand-400 bg-brand-50 p-3 text-center">
                    <p className="flex items-center justify-center gap-1 text-xs font-semibold text-ink-700"><KeyRound className="h-3.5 w-3.5" /> Code de retrait · compartiment {order.compartmentNumber}</p>
                    <p className="font-mono text-3xl font-black tracking-[.25em]">{code}</p>
                    <p className="text-[11px] text-ink-500">Visible uniquement par ce client.</p>
                  </div>
                ) : <p className="text-xs text-ink-500">Le code arrive ici dès que le repas est verrouillé dans le coffre.</p>}
              </>
            ) : <p className="text-xs text-ink-500">Aucune commande en cours.</p>}
          </div>
          <div className="card space-y-1 text-sm">
            <p className="flex items-center gap-2 font-semibold"><ChefHat className="h-4 w-4 text-brand-500" /> Restaurant</p>
            <p className="text-xs text-ink-500">{order && !done ? `${order.restaurantName} — commande n°${order.id}${order.compartmentNumber ? `, compartiment ${order.compartmentNumber} (${order.compartmentState ? compartmentStateLabel[order.compartmentState] : ''})` : ''}` : 'En attente de commande.'}</p>
          </div>
          <div className="card space-y-1 text-sm">
            <p className="flex items-center gap-2 font-semibold"><Bike className="h-4 w-4 text-brand-500" /> Livreur</p>
            <p className="text-xs text-ink-500">{mission && !done ? `Mission n°${mission.id} — ${deliveryStatusLabel[mission.status]}. Il ne voit jamais le code et ne peut ouvrir aucun compartiment.` : 'Aucune mission en cours.'}</p>
          </div>
          <div className="card">
            <p className="mb-2 text-sm font-semibold">Déroulé</p>
            <ol className="space-y-1.5">
              {steps.map((s, i) => {
                const Icon = whoIcon[s.who];
                return (
                  <li key={s.title} className={`flex items-center gap-2 text-xs ${i === step ? 'font-bold text-ink-900' : i < step ? 'text-ink-500' : 'text-ink-300'}`}>
                    <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full ${i < step ? 'bg-leaf-500 text-white' : i === step ? 'bg-brand-500 text-white' : 'bg-ink-100'}`}>{i < step ? <Check className="h-3 w-3" /> : <Icon className="h-3 w-3" />}</span>
                    {s.title}
                  </li>
                );
              })}
            </ol>
          </div>
          {log.length > 0 && (
            <div className="card">
              <p className="mb-2 text-sm font-semibold">Journal</p>
              <ul className="max-h-48 space-y-1 overflow-y-auto text-[11px] text-ink-700">{log.map((l, i) => <li key={i}>{l}</li>)}</ul>
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
