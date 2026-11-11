import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Check, Eye, EyeOff, KeyRound, Phone, Receipt, ShoppingBag } from 'lucide-react';
import { errorMessage, get, post } from '../../api/client';
import { dateTime, deliveryStatusLabel, fcfa, orderStatusLabel, paymentMethodLabel, paymentStatusLabel, timeOnly } from '../../api/labels';
import type { AppConfig, CodeView, Complaint, GroupView, OrderStatus, OrderView, PaymentMethod, Tracking } from '../../api/types';
import { BoxSimulator } from '../../components/BoxSimulator';
import { MapView, type MapPoint } from '../../components/MapView';
import { Badge, Empty, ErrorBox, Field, PageTitle, Spinner, type Tone } from '../../components/ui';
import { useCart } from '../../stores/cart';
import { toast } from '../../stores/toast';

const tone: Record<OrderStatus, Tone> = {
  AWAITING_PAYMENT: 'orange', PENDING: 'blue', ACCEPTED: 'blue', READY: 'blue', RESERVED: 'blue', LOADED: 'dark', IN_TRANSIT: 'orange', ARRIVED: 'orange', COLLECTED: 'green', REJECTED: 'red', CANCELLED: 'gray',
};
const steps: OrderStatus[] = ['PENDING', 'ACCEPTED', 'READY', 'LOADED', 'IN_TRANSIT', 'ARRIVED', 'COLLECTED'];
const stepLabel = ['Envoyée', 'Préparation', 'Prête', 'Verrouillée', 'En route', 'Arrivé', 'Retirée'];
const rank = (s: OrderStatus) => (s === 'RESERVED' ? 2 : steps.indexOf(s));

export function OrdersPage() {
  const list = useQuery({ queryKey: ['orders', 'mine'], queryFn: () => get<GroupView[]>('/customer/orders') });
  if (list.isLoading) return <Spinner />;
  if (list.isError) return <ErrorBox error={list.error} retry={() => list.refetch()} />;
  return (
    <div>
      <PageTitle icon={<ShoppingBag className="h-6 w-6" />} title="Mes commandes" subtitle="Suivi, codes de retrait et historique." />
      {list.data?.length === 0 && <Empty title="Aucune commande" hint="Vos commandes apparaîtront ici." action={<Link className="btn-primary" to="/">Commander</Link>} />}
      <div className="space-y-3">
        {list.data?.map((g) => (
          <Link key={g.id} to={`/commandes/${g.id}`} className="card block transition hover:-translate-y-0.5">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-bold">Commande n°{g.id}</p>
              <p className="text-sm text-ink-500">{dateTime(g.createdAt)} · {fcfa(g.total)}</p>
            </div>
            <div className="mt-2 flex flex-wrap gap-2">
              {g.orders.map((o) => <Badge key={o.id} tone={tone[o.status]}>{o.restaurantName} : {orderStatusLabel[o.status]}</Badge>)}
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}

function Progress({ order }: { order: OrderView }) {
  if (['REJECTED', 'CANCELLED', 'AWAITING_PAYMENT'].includes(order.status)) return null;
  const current = rank(order.status);
  return (
    <ol className="flex items-start gap-1">
      {stepLabel.map((label, i) => (
        <li key={label} className="flex flex-1 flex-col items-center gap-1 text-center">
          <span className={`flex h-6 w-6 items-center justify-center rounded-full text-[11px] font-bold ${i <= current ? 'bg-brand-500 text-white' : 'bg-ink-100 text-ink-500'}`}>{i < current ? <Check className="h-3.5 w-3.5" /> : i + 1}</span>
          <span className={`text-[10px] leading-tight ${i <= current ? 'font-semibold text-ink-900' : 'text-ink-500'}`}>{label}</span>
        </li>
      ))}
    </ol>
  );
}

/** Code de retrait : demande au serveur a la demande, jamais conserve dans la liste des commandes. */
function WithdrawalCode({ order }: { order: OrderView }) {
  const [shown, setShown] = useState(false);
  const queryClient = useQueryClient();
  const code = useQuery({ queryKey: ['orders', 'code', order.id], queryFn: () => get<CodeView>(`/customer/order-items/${order.id}/code`), enabled: shown && order.codeActive, staleTime: 0, gcTime: 0, retry: false });
  const renew = useMutation({
    mutationFn: () => post<CodeView>(`/customer/order-items/${order.id}/code/renew`),
    onSuccess: () => { toast.success('Nouveau code généré.'); setShown(true); queryClient.invalidateQueries({ queryKey: ['orders'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const carried = ['LOADED', 'IN_TRANSIT', 'ARRIVED'].includes(order.status);
  if (!carried) return null;
  return (
    <div className="rounded-2xl border-2 border-dashed border-brand-400 bg-brand-50 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <KeyRound className="h-5 w-5 text-brand-600" />
        <div className="flex-1">
          <p className="text-sm font-semibold">Code de retrait — compartiment {order.compartmentNumber} du {order.boxName}</p>
          {order.codeActive ? (
            <p className="font-mono text-3xl font-black tracking-[.3em] text-ink-900">{shown && code.data ? code.data.code : '••••••'}</p>
          ) : (
            <p className="text-sm text-red-700">Votre code a expiré.</p>
          )}
          {order.codeActive && <p className="text-xs text-ink-500">Valable jusqu'à {timeOnly(order.codeExpiresAt)}. Usage unique. Ne le communiquez à personne, pas même au livreur.</p>}
          {code.isError && <p className="text-xs text-red-600">{errorMessage(code.error)}</p>}
        </div>
        {order.codeActive ? (
          <button className="btn-dark btn-sm" onClick={() => setShown((s) => !s)}>{shown ? <><EyeOff className="h-3.5 w-3.5" /> Masquer</> : <><Eye className="h-3.5 w-3.5" /> Afficher</>}</button>
        ) : (
          <button className="btn-primary btn-sm" disabled={renew.isPending} onClick={() => renew.mutate()}>Demander un nouveau code</button>
        )}
      </div>
    </div>
  );
}

function TrackingMap({ group }: { group: GroupView }) {
  const tracking = useQuery({ queryKey: ['tracking', group.id], queryFn: () => get<Tracking[]>(`/customer/orders/${group.id}/tracking`), refetchInterval: 10000 });
  if (tracking.isLoading) return <Spinner label="Chargement du suivi…" />;
  if (tracking.isError) return <ErrorBox error={tracking.error} retry={() => tracking.refetch()} />;
  if (!tracking.data || tracking.data.length === 0) return <p className="text-sm text-ink-500">Le suivi sur la carte démarre dès qu'un livreur est affecté.</p>;
  return (
    <div className="space-y-4">
      {tracking.data.map((t) => {
        const points: MapPoint[] = [
          { lat: t.destLat, lng: t.destLng, label: 'Votre adresse de livraison', kind: 'destination' },
          ...t.pickups.map((p) => ({ lat: p.latitude, lng: p.longitude, label: `Départ : ${p.restaurantName}`, kind: 'pickup' as const })),
        ];
        if (t.lastLocation) points.push({ lat: t.lastLocation.latitude, lng: t.lastLocation.longitude, label: `${t.courierName} — ${timeOnly(t.lastLocation.recordedAt)}`, kind: 'courier' });
        return (
          <div key={t.deliveryId} className="space-y-2">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge tone="orange">{deliveryStatusLabel[t.status]}</Badge>
              <span className="font-semibold">{t.courierName}</span>
              <a className="inline-flex items-center gap-1 text-brand-600" href={`tel:${t.courierPhone}`}><Phone className="h-3.5 w-3.5" /> {t.courierPhone}</a>
              <span className="text-ink-500">· {t.boxName}</span>
              {t.lastLocation?.simulated && <span className="rounded bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">POSITION SIMULÉE (TEST)</span>}
            </div>
            <MapView points={points} path={t.path.map((p) => [p.latitude, p.longitude])} />
            <p className="text-xs text-ink-500">
              {t.lastLocation ? `Dernière position reçue à ${timeOnly(t.lastLocation.recordedAt)}${t.lastLocation.accuracyM ? ` (précision ± ${Math.round(t.lastLocation.accuracyM)} m)` : ''}.` : "La position du livreur s'affiche dès qu'il prend le coffre en charge et active son GPS."}
            </p>
          </div>
        );
      })}
    </div>
  );
}

export function OrderDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const addLines = useCart((c) => c.addLines);
  const queryClient = useQueryClient();
  const group = useQuery({ queryKey: ['orders', 'group', id], queryFn: () => get<GroupView>(`/customer/orders/${id}`), refetchInterval: 15000 });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['orders'] });
  const pay = useMutation({ mutationFn: (success: boolean) => post(`/customer/orders/${id}/sandbox-payment`, { success }), onSuccess: refresh, onError: (e) => toast.error(errorMessage(e)) });
  const cancel = useMutation({ mutationFn: (orderId: number) => post(`/customer/order-items/${orderId}/cancel`), onSuccess: () => { toast.success('Commande annulée.'); refresh(); }, onError: (e) => toast.error(errorMessage(e)) });
  const config = useQuery({ queryKey: ['config'], queryFn: () => get<AppConfig>('/config') });
  const [retryMethod, setRetryMethod] = useState<PaymentMethod | null>(null);
  const retry = useMutation({
    mutationFn: (method: PaymentMethod) => post(`/customer/orders/${id}/retry-payment`, { paymentMethod: method }),
    onSuccess: () => { toast.success('Nouvelle tentative de paiement lancée.'); refresh(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  if (group.isLoading) return <Spinner />;
  if (group.isError || !group.data) return <ErrorBox error={group.error} retry={() => group.refetch()} />;
  const g = group.data;
  const paymentFailed = g.payment?.status === 'FAILED' && g.orders.some((o) => o.status === 'AWAITING_PAYMENT');
  // Commande entierement annulee ou refusee : le client peut remettre les memes plats dans son panier.
  const allClosed = g.orders.every((o) => o.status === 'CANCELLED' || o.status === 'REJECTED');
  const reorder = () => {
    addLines(g.orders.flatMap((o) => o.items.map((i) => ({ dishId: i.dishId, name: i.name, price: i.unitPrice, quantity: i.quantity, restaurantId: o.restaurantId, restaurantName: o.restaurantName }))));
    toast.success('Les plats sont de nouveau dans votre panier.');
    navigate('/panier');
  };
  const chosenMethod = retryMethod ?? g.payment?.method ?? 'CASH_ON_DELIVERY';
  const boxIds = Array.from(new Set(g.orders.filter((o) => o.boxId && ['LOADED', 'IN_TRANSIT', 'ARRIVED'].includes(o.status)).map((o) => o.boxId as number)));
  const awaitingSandbox = g.payment?.status === 'PENDING' && g.payment.provider === 'sandbox';
  return (
    <div className="space-y-4">
      <Link to="/commandes" className="inline-flex items-center gap-1 text-sm font-semibold text-ink-500 hover:text-ink-900"><ArrowLeft className="h-4 w-4" /> Mes commandes</Link>
      <PageTitle icon={<Receipt className="h-6 w-6" />} title={`Commande n°${g.id}`} subtitle={`${dateTime(g.createdAt)} · ${g.deliveryAddress}`} />

      {awaitingSandbox && (
        <div className="card space-y-2 border-amber-300 bg-amber-50">
          <p className="font-semibold">Paiement {paymentMethodLabel[g.payment!.method]} — environnement de test</p>
          <p className="text-sm text-ink-700">Aucun argent réel n'est débité. Choisissez l'issue du paiement de {fcfa(g.payment!.amount)} pour tester la suite du parcours.</p>
          <div className="flex gap-2">
            <button className="btn-primary btn-sm" disabled={pay.isPending} onClick={() => pay.mutate(true)}>Simuler un paiement réussi</button>
            <button className="btn-danger btn-sm" disabled={pay.isPending} onClick={() => pay.mutate(false)}>Simuler un échec</button>
          </div>
        </div>
      )}

      {allClosed && (
        <div className="card flex flex-wrap items-center justify-between gap-3 border-brand-100 bg-brand-50">
          <div>
            <p className="font-semibold">{g.payment?.status === 'FAILED' ? "Le paiement n'a pas abouti et cette commande a été annulée." : 'Cette commande a été annulée.'}</p>
            <p className="text-sm text-ink-700">Vous pouvez la repasser en un clic et choisir votre moyen de paiement.</p>
          </div>
          <button className="btn-primary" onClick={reorder}>Commander à nouveau</button>
        </div>
      )}

      {paymentFailed && (
        <div className="card space-y-3 border-red-200 bg-red-50">
          <div>
            <p className="font-semibold text-red-800">Le paiement de {fcfa(g.payment!.amount)} n'a pas abouti</p>
            <p className="text-sm text-ink-700">Votre commande est conservée. Réessayez avec le même moyen de paiement ou choisissez-en un autre.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select className="input w-auto" value={chosenMethod} onChange={(e) => setRetryMethod(e.target.value as PaymentMethod)} aria-label="Moyen de paiement">
              {(config.data?.paymentMethods ?? [chosenMethod]).map((m) => <option key={m} value={m}>{paymentMethodLabel[m]}</option>)}
            </select>
            <button className="btn-primary" disabled={retry.isPending} onClick={() => retry.mutate(chosenMethod)}>Réessayer le paiement</button>
          </div>
        </div>
      )}

      {g.orders.map((o) => (
        <div key={o.id} className="card space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div><h2>{o.restaurantName}</h2><p className="text-xs text-ink-500">Sous-commande n°{o.id}</p></div>
            <Badge tone={tone[o.status]}>{orderStatusLabel[o.status]}</Badge>
          </div>
          <Progress order={o} />
          {o.rejectReason && <p className="text-sm text-red-700">Motif du refus : {o.rejectReason}</p>}
          <ul className="text-sm">{o.items.map((i) => <li key={i.dishId} className="flex justify-between border-t border-ink-100 py-1"><span>{i.quantity} × {i.name}</span><span>{fcfa(i.unitPrice * i.quantity)}</span></li>)}</ul>
          <WithdrawalCode order={o} />
          {(o.status === 'PENDING' || o.status === 'AWAITING_PAYMENT') && <button className="btn-danger btn-sm" disabled={cancel.isPending} onClick={() => cancel.mutate(o.id)}>Annuler cette commande</button>}
          <details className="text-xs text-ink-500"><summary className="cursor-pointer font-semibold">Historique</summary>
            <ul className="mt-1 space-y-0.5">{o.history.map((h, i) => <li key={i}>{dateTime(h.at)} — {orderStatusLabel[h.status]}{h.note ? ` (${h.note})` : ''}</li>)}</ul>
          </details>
        </div>
      ))}

      <div className="card space-y-1 text-sm">
        <div className="flex justify-between"><span className="text-ink-500">Sous-total</span><span>{fcfa(g.subtotal)}</span></div>
        <div className="flex justify-between"><span className="text-ink-500">Livraison</span><span>{fcfa(g.deliveryFee)}</span></div>
        <div className="flex justify-between font-bold"><span>Total</span><span>{fcfa(g.total)}</span></div>
        {g.payment && <p className="pt-1 text-xs text-ink-500">{paymentMethodLabel[g.payment.method]} — {paymentStatusLabel[g.payment.status]}{g.payment.method === 'CASH_ON_DELIVERY' && g.payment.status === 'PENDING' ? `. Remettez ${fcfa(g.payment.amount)} au livreur : votre code ouvre le compartiment une fois le paiement confirmé.` : ''}</p>}
      </div>

      <div className="card space-y-3"><h2>Suivi de la livraison</h2><TrackingMap group={g} /></div>

      {boxIds.map((boxId) => (
        <div key={boxId} className="space-y-2">
          <h2>Retrait au coffre</h2>
          <p className="text-sm text-ink-500">Prototype virtuel : ce coffre représente celui que le livreur vous présente. Saisissez votre code sur le clavier, retirez votre repas puis refermez la porte.</p>
          <BoxSimulator boxId={boxId} keypad />
        </div>
      ))}
    </div>
  );
}

const complaintSchema = z.object({ orderId: z.string(), subject: z.string().trim().min(3, 'Objet obligatoire.').max(160), message: z.string().trim().min(10, 'Décrivez le problème (10 caractères minimum).').max(2000) });

export function ComplaintsPage() {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['complaints'], queryFn: () => get<Complaint[]>('/customer/complaints') });
  const orders = useQuery({ queryKey: ['orders', 'mine'], queryFn: () => get<GroupView[]>('/customer/orders') });
  const form = useForm<z.infer<typeof complaintSchema>>({ resolver: zodResolver(complaintSchema), defaultValues: { orderId: '' } });
  const create = useMutation({
    mutationFn: (v: z.infer<typeof complaintSchema>) => post('/customer/complaints', { subject: v.subject, message: v.message, orderId: v.orderId ? Number(v.orderId) : null }),
    onSuccess: () => { toast.success('Réclamation envoyée.'); form.reset({ orderId: '', subject: '', message: '' }); queryClient.invalidateQueries({ queryKey: ['complaints'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const statusLabel = { OPEN: 'Ouverte', IN_PROGRESS: 'En cours', RESOLVED: 'Résolue' };
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <form className="card h-fit space-y-3" onSubmit={form.handleSubmit((v) => create.mutate(v))} noValidate>
        <h1>Signaler un problème</h1>
        <Field label="Commande concernée (facultatif)">
          <select className="input" {...form.register('orderId')}>
            <option value="">Aucune</option>
            {orders.data?.flatMap((g) => g.orders).map((o) => <option key={o.id} value={o.id}>n°{o.id} — {o.restaurantName}</option>)}
          </select>
        </Field>
        <Field label="Objet" error={form.formState.errors.subject?.message}><input className="input" {...form.register('subject')} /></Field>
        <Field label="Message" error={form.formState.errors.message?.message}><textarea className="input min-h-[120px]" {...form.register('message')} /></Field>
        <button className="btn-primary" disabled={create.isPending}>Envoyer</button>
      </form>
      <div className="space-y-3">
        <h2>Mes réclamations</h2>
        {list.isLoading && <Spinner />}
        {list.isError && <ErrorBox error={list.error} retry={() => list.refetch()} />}
        {list.data?.length === 0 && <p className="text-sm text-ink-500">Aucune réclamation.</p>}
        {list.data?.map((c) => (
          <div key={c.id} className="card space-y-1 text-sm">
            <div className="flex items-center justify-between gap-2"><p className="font-semibold">{c.subject}</p><Badge tone={c.status === 'RESOLVED' ? 'green' : 'orange'}>{statusLabel[c.status]}</Badge></div>
            <p className="text-ink-700">{c.message}</p>
            {c.response && <p className="rounded-lg bg-ink-50 p-2"><span className="font-semibold">Réponse :</span> {c.response}</p>}
            <p className="text-xs text-ink-500">{dateTime(c.createdAt)}{c.orderId ? ` · commande n°${c.orderId}` : ''}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
