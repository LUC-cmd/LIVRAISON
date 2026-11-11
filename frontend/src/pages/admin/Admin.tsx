import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage, get, post, put } from '../../api/client';
import { compartmentStateLabel, dateTime, deliveryStatusLabel, fcfa, orderStatusLabel, paymentMethodLabel, paymentStatusLabel, roleLabel, sizeLabel } from '../../api/labels';
import type { BoxView, CompartmentState, Complaint, DayStat, DeliveryStatus, GroupView, PaymentMethod, PaymentStatus, RestaurantEntity, Role, SizeClass, User } from '../../api/types';
import { BoxSimulator } from '../../components/BoxSimulator';
import { Badge, BarChart, Empty, ErrorBox, Field, PageTitle, Spinner, Stat } from '../../components/ui';
import { hasRole, useSession } from '../../stores/session';
import { toast } from '../../stores/toast';

const fail = (e: unknown) => toast.error(errorMessage(e));

// ---------------------------------------------------------------- Tableau de bord

interface Dashboard { customers: number; restaurants: number; couriers: number; restaurantsPending: number; ordersTotal: number; ordersInProgress: number; ordersCollected: number; grossSales: number; commissions: number; deliveryFees: number; activeDeliveries: number; boxes: number; boxesOffline: number; compartments: Record<CompartmentState, number>; openComplaints: number; lastDays: DayStat[] }

export function AdminDashboardPage() {
  const queryClient = useQueryClient();
  const d = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: () => get<Dashboard>('/admin/dashboard'), refetchInterval: 20000 });
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [role, setRole] = useState<Role | ''>('');
  const broadcast = useMutation({
    mutationFn: () => post<{ recipients: number }>('/admin/notifications/broadcast', { title, body, role: role || null }),
    onSuccess: (r) => { toast.success(`Message envoyé à ${r.recipients} compte(s).`); setTitle(''); setBody(''); queryClient.invalidateQueries({ queryKey: ['notifications'] }); },
    onError: fail,
  });
  if (d.isLoading) return <Spinner />;
  if (d.isError || !d.data) return <ErrorBox error={d.error} retry={() => d.refetch()} />;
  const s = d.data;
  return (
    <div className="space-y-4">
      <PageTitle title="Tableau de bord" subtitle="Vue d'ensemble de la plateforme." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Ventes (retirées)" value={fcfa(s.grossSales)} hint={`${s.ordersCollected} commande(s)`} />
        <Stat label="Commissions" value={fcfa(s.commissions)} hint={`+ ${fcfa(s.deliveryFees)} de frais de livraison encaissés`} />
        <Stat label="Commandes en cours" value={s.ordersInProgress} hint={`${s.ordersTotal} au total`} />
        <Stat label="Livraisons actives" value={s.activeDeliveries} />
        <Stat label="Clients" value={s.customers} />
        <Stat label="Restaurants" value={s.restaurants} hint={`${s.restaurantsPending} à valider`} />
        <Stat label="Livreurs" value={s.couriers} />
        <Stat label="Coffres" value={s.boxes} hint={`${s.boxesOffline} hors connexion · ${s.openComplaints} réclamation(s) ouverte(s)`} />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card"><h2 className="mb-3">Ventes des 7 derniers jours</h2><BarChart data={s.lastDays.map((x) => ({ label: x.day.slice(8, 10) + '/' + x.day.slice(5, 7), value: x.revenue }))} format={(n) => n.toLocaleString('fr-FR')} /></div>
        <div className="card"><h2 className="mb-3">État des compartiments</h2>
          <div className="flex flex-wrap gap-2">{(Object.keys(s.compartments) as CompartmentState[]).map((k) => <Badge key={k} tone={k === 'FAULT' && s.compartments[k] > 0 ? 'red' : k === 'AVAILABLE' ? 'green' : 'gray'}>{compartmentStateLabel[k]} : {s.compartments[k]}</Badge>)}</div>
        </div>
      </div>
      <div className="card space-y-3">
        <h2>Envoyer une notification</h2>
        <div className="grid gap-3 sm:grid-cols-[200px_1fr]">
          <select className="input" value={role} onChange={(e) => setRole(e.target.value as Role | '')} aria-label="Destinataires"><option value="">Tous les comptes</option>{(['CUSTOMER', 'RESTAURANT', 'COURIER'] as Role[]).map((r) => <option key={r} value={r}>{roleLabel[r]}s</option>)}</select>
          <input className="input" placeholder="Titre" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={160} />
        </div>
        <textarea className="input min-h-[70px]" placeholder="Message" value={body} onChange={(e) => setBody(e.target.value)} maxLength={600} />
        <button className="btn-dark" disabled={!title.trim() || !body.trim() || broadcast.isPending} onClick={() => broadcast.mutate()}>Envoyer</button>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Utilisateurs

export function AdminUsersPage() {
  const me = useSession((s) => s.user);
  const queryClient = useQueryClient();
  const [role, setRole] = useState<Role | ''>('');
  const users = useQuery({ queryKey: ['admin', 'users', role], queryFn: () => get<User[]>('/admin/users', role ? { role } : undefined) });
  const toggle = useMutation({ mutationFn: (u: User) => post(`/admin/users/${u.id}/${u.status === 'ACTIVE' ? 'suspend' : 'activate'}`), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }), onError: fail });
  return (
    <div>
      <PageTitle title="Utilisateurs" action={<select className="input w-auto" value={role} onChange={(e) => setRole(e.target.value as Role | '')} aria-label="Filtrer par rôle"><option value="">Tous les rôles</option>{(Object.keys(roleLabel) as Role[]).map((r) => <option key={r} value={r}>{roleLabel[r]}</option>)}</select>} />
      {users.isLoading && <Spinner />}
      {users.isError && <ErrorBox error={users.error} retry={() => users.refetch()} />}
      {users.data && (
        <div className="card overflow-x-auto"><table className="table"><thead><tr><th>Nom</th><th>Email</th><th>Téléphone</th><th>Rôles</th><th>Statut</th><th></th></tr></thead>
          <tbody>{users.data.map((u) => (
            <tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td><td>{u.phone}</td><td>{u.roles.map((r) => roleLabel[r]).join(', ')}</td>
              <td><Badge tone={u.status === 'ACTIVE' ? 'green' : 'red'}>{u.status === 'ACTIVE' ? 'Actif' : 'Suspendu'}</Badge></td>
              <td>{u.id !== me?.id && !u.roles.includes('SUPERADMIN') && (!u.roles.includes('ADMIN') || hasRole(me, 'SUPERADMIN')) && <button className={u.status === 'ACTIVE' ? 'btn-danger btn-sm' : 'btn-ghost btn-sm'} disabled={toggle.isPending} onClick={() => toggle.mutate(u)}>{u.status === 'ACTIVE' ? 'Suspendre' : 'Réactiver'}</button>}</td></tr>
          ))}</tbody></table></div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- Restaurants

export function AdminRestaurantsPage() {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['admin', 'restaurants'], queryFn: () => get<{ restaurant: RestaurantEntity; ownerName: string; ownerEmail: string }[]>('/admin/restaurants') });
  const act = useMutation({ mutationFn: ({ id, action }: { id: number; action: 'approve' | 'suspend' }) => post(`/admin/restaurants/${id}/${action}`), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin'] }); queryClient.invalidateQueries({ queryKey: ['restaurants'] }); }, onError: fail });
  if (list.isLoading) return <Spinner />;
  if (list.isError) return <ErrorBox error={list.error} retry={() => list.refetch()} />;
  const label = { PENDING: 'À valider', APPROVED: 'Validé', SUSPENDED: 'Suspendu' };
  return (
    <div>
      <PageTitle title="Restaurants" subtitle="Un restaurant n'est visible des clients qu'après validation." />
      {list.data?.length === 0 && <Empty title="Aucun restaurant inscrit" />}
      <div className="grid gap-3 md:grid-cols-2">
        {list.data?.map(({ restaurant: r, ownerName, ownerEmail }) => (
          <div key={r.id} className="card space-y-2">
            <div className="flex items-center justify-between gap-2"><p className="font-bold">{r.name}</p><Badge tone={r.status === 'APPROVED' ? 'green' : r.status === 'PENDING' ? 'orange' : 'red'}>{label[r.status]}</Badge></div>
            <p className="text-sm text-ink-500">{r.addressLine}, {r.city} · {r.phone}</p>
            <p className="text-xs text-ink-500">Responsable : {ownerName} ({ownerEmail}) · inscrit le {dateTime(r.createdAt)}</p>
            <div className="flex gap-2">
              {r.status !== 'APPROVED' && <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: r.id, action: 'approve' })}>Valider</button>}
              {r.status !== 'SUSPENDED' && <button className="btn-danger btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: r.id, action: 'suspend' })}>Suspendre</button>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Commandes, paiements, livraisons

export function AdminOrdersPage() {
  const list = useQuery({ queryKey: ['admin', 'orders'], queryFn: () => get<GroupView[]>('/admin/orders'), refetchInterval: 20000 });
  if (list.isLoading) return <Spinner />;
  if (list.isError) return <ErrorBox error={list.error} retry={() => list.refetch()} />;
  return (
    <div>
      <PageTitle title="Commandes" subtitle="Les 200 commandes les plus récentes. Les codes de retrait ne sont jamais affichés ici." />
      {list.data?.length === 0 && <Empty title="Aucune commande" />}
      <div className="card overflow-x-auto"><table className="table"><thead><tr><th>N°</th><th>Date</th><th>Client</th><th>Sous-commandes</th><th>Total</th><th>Paiement</th></tr></thead>
        <tbody>{list.data?.map((g) => (
          <tr key={g.id}><td>{g.id}</td><td>{dateTime(g.createdAt)}</td><td>{g.customerName}<br /><span className="text-xs text-ink-500">{g.deliveryAddress}</span></td>
            <td className="space-y-1">{g.orders.map((o) => <div key={o.id}>n°{o.id} {o.restaurantName} — <span className="font-semibold">{orderStatusLabel[o.status]}</span>{o.compartmentNumber ? ` · ${o.boxName}, compartiment ${o.compartmentNumber}` : ''}</div>)}</td>
            <td>{fcfa(g.total)}</td><td>{g.payment ? `${paymentMethodLabel[g.payment.method]} — ${paymentStatusLabel[g.payment.status]}` : '—'}</td></tr>
        ))}</tbody></table></div>
    </div>
  );
}

interface PaymentRow { payment: { id: number; groupId: number; method: PaymentMethod; status: PaymentStatus; amount: number; provider: string; providerRef: string | null; createdAt: string }; transactions: { id: number; type: 'CHARGE' | 'REFUND'; status: string; amount: number; detail: string | null; createdAt: string }[] }

export function AdminPaymentsPage() {
  const list = useQuery({ queryKey: ['admin', 'payments'], queryFn: () => get<PaymentRow[]>('/admin/payments') });
  if (list.isLoading) return <Spinner />;
  if (list.isError) return <ErrorBox error={list.error} retry={() => list.refetch()} />;
  return (
    <div>
      <PageTitle title="Paiements" />
      {list.data?.length === 0 && <Empty title="Aucun paiement" />}
      <div className="card overflow-x-auto"><table className="table"><thead><tr><th>Commande</th><th>Date</th><th>Moyen</th><th>Montant</th><th>Statut</th><th>Mouvements</th></tr></thead>
        <tbody>{list.data?.map(({ payment: p, transactions }) => (
          <tr key={p.id}><td>n°{p.groupId}</td><td>{dateTime(p.createdAt)}</td><td>{paymentMethodLabel[p.method]}<br /><span className="text-xs text-ink-500">{p.provider}{p.providerRef ? ` · ${p.providerRef}` : ''}</span></td><td>{fcfa(p.amount)}</td>
            <td><Badge tone={p.status === 'SUCCEEDED' ? 'green' : p.status === 'PENDING' ? 'orange' : 'red'}>{paymentStatusLabel[p.status]}</Badge></td>
            <td className="text-xs">{transactions.map((t) => <div key={t.id}>{dateTime(t.createdAt)} · {t.type === 'REFUND' ? 'Remboursement' : 'Encaissement'} {fcfa(t.amount)} · {t.status}{t.detail ? ` · ${t.detail}` : ''}</div>)}</td></tr>
        ))}</tbody></table></div>
    </div>
  );
}

interface DeliveryRow { delivery: { id: number; groupId: number; status: DeliveryStatus; courierEarning: number; createdAt: string; completedAt: string | null }; courierName: string; boxName: string }

export function AdminDeliveriesPage() {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['admin', 'deliveries'], queryFn: () => get<DeliveryRow[]>('/admin/deliveries'), refetchInterval: 20000 });
  const couriers = useQuery({ queryKey: ['admin', 'couriers'], queryFn: () => get<{ user: User; available: boolean; vehicle: string; boxName: string | null }[]>('/admin/couriers') });
  const cancel = useMutation({ mutationFn: (id: number) => post(`/admin/deliveries/${id}/cancel`, { reason: "Livraison annulée par l'administrateur." }), onSuccess: () => { toast.success('Livraison annulée, compartiments libérés.'); queryClient.invalidateQueries({ queryKey: ['admin'] }); queryClient.invalidateQueries({ queryKey: ['boxes'] }); }, onError: fail });
  if (list.isLoading) return <Spinner />;
  if (list.isError) return <ErrorBox error={list.error} retry={() => list.refetch()} />;
  return (
    <div className="space-y-4">
      <PageTitle title="Livraisons et livreurs" />
      <div className="card overflow-x-auto"><h2 className="mb-2">Livreurs</h2><table className="table"><thead><tr><th>Nom</th><th>Téléphone</th><th>Disponibilité</th><th>Coffre</th></tr></thead>
        <tbody>{couriers.data?.map((c) => <tr key={c.user.id}><td>{c.user.fullName}</td><td>{c.user.phone}</td><td><Badge tone={c.available ? 'green' : 'gray'}>{c.available ? 'Disponible' : 'Indisponible'}</Badge></td><td>{c.boxName ?? 'Aucun'}</td></tr>)}</tbody></table></div>
      <div className="card overflow-x-auto"><h2 className="mb-2">Livraisons</h2>
        {list.data?.length === 0 ? <p className="text-sm text-ink-500">Aucune livraison.</p> : (
          <table className="table"><thead><tr><th>N°</th><th>Commande</th><th>Livreur</th><th>Coffre</th><th>Statut</th><th>Créée</th><th>Gain livreur</th><th></th></tr></thead>
            <tbody>{list.data?.map(({ delivery: d, courierName, boxName }) => (
              <tr key={d.id}><td>{d.id}</td><td>n°{d.groupId}</td><td>{courierName}</td><td>{boxName}</td><td>{deliveryStatusLabel[d.status]}</td><td>{dateTime(d.createdAt)}</td><td>{d.status === 'COMPLETED' ? fcfa(d.courierEarning) : '—'}</td>
                <td>{(d.status === 'OFFERED' || d.status === 'ACCEPTED') && <button className="btn-danger btn-sm" disabled={cancel.isPending} onClick={() => cancel.mutate(d.id)}>Annuler</button>}</td></tr>
            ))}</tbody></table>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Coffres

const boxSchema = z.object({ name: z.string().trim().min(2, 'Nom obligatoire.').max(80), serial: z.string().regex(/^[A-Za-z0-9-]{3,40}$/, '3 à 40 lettres, chiffres ou tirets.'), courierId: z.string() });

export function AdminBoxesPage() {
  const queryClient = useQueryClient();
  const boxes = useQuery({ queryKey: ['boxes', 'list'], queryFn: () => get<BoxView[]>('/boxes'), refetchInterval: 15000 });
  const couriers = useQuery({ queryKey: ['admin', 'couriers'], queryFn: () => get<{ user: User; boxId: number | null }[]>('/admin/couriers') });
  const [sizes, setSizes] = useState<SizeClass[]>(['SMALL', 'SMALL', 'MEDIUM', 'MEDIUM', 'MEDIUM', 'LARGE']);
  const [secret, setSecret] = useState<{ name: string; uid: string; secret: string } | null>(null);
  const form = useForm<z.infer<typeof boxSchema>>({ resolver: zodResolver(boxSchema), defaultValues: { name: '', serial: '', courierId: '' } });
  const refresh = () => { queryClient.invalidateQueries({ queryKey: ['boxes'] }); queryClient.invalidateQueries({ queryKey: ['admin'] }); };
  const create = useMutation({
    mutationFn: (v: z.infer<typeof boxSchema>) => post<{ box: BoxView; deviceSecret: string }>('/admin/boxes', { name: v.name, serial: v.serial, sizes, courierId: v.courierId ? Number(v.courierId) : null }),
    onSuccess: (r) => { toast.success('Coffre créé.'); setSecret({ name: r.box.name, uid: r.box.device?.deviceUid ?? '', secret: r.deviceSecret }); form.reset(); refresh(); },
    onError: fail,
  });
  const assign = useMutation({ mutationFn: ({ id, courierId }: { id: number; courierId: number | null }) => put(`/admin/boxes/${id}/courier`, { courierId }), onSuccess: refresh, onError: fail });
  const status = useMutation({ mutationFn: (b: BoxView) => put(`/admin/boxes/${b.id}/status`, { status: b.status === 'ACTIVE' ? 'OUT_OF_SERVICE' : 'ACTIVE' }), onSuccess: refresh, onError: fail });

  return (
    <div className="space-y-5">
      <PageTitle title="Coffres et compartiments" subtitle="État réel connu du serveur, matériel simulé et injection de pannes pour les tests." />
      {boxes.isLoading && <Spinner />}
      {boxes.isError && <ErrorBox error={boxes.error} retry={() => boxes.refetch()} />}
      {boxes.data?.length === 0 && <Empty title="Aucun coffre" hint="Créez un coffre avec le formulaire ci-dessous." />}
      {boxes.data?.map((b) => (
        <section key={b.id} className="space-y-2">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <Badge tone={b.status === 'ACTIVE' ? 'green' : 'red'}>{b.status === 'ACTIVE' ? 'En service' : 'Hors service'}</Badge>
            <span>Livreur :</span>
            <select className="input w-auto !py-1.5" value={b.courierId ?? ''} disabled={assign.isPending} onChange={(e) => assign.mutate({ id: b.id, courierId: e.target.value ? Number(e.target.value) : null })} aria-label={`Livreur du ${b.name}`}>
              <option value="">Aucun</option>
              {couriers.data?.filter((c) => c.boxId === null || c.boxId === b.id).map((c) => <option key={c.user.id} value={c.user.id}>{c.user.fullName}</option>)}
            </select>
            {b.courierId && <Badge tone={b.courierAvailable ? 'green' : 'gray'}>{b.courierAvailable ? 'Livreur disponible' : 'Livreur indisponible'}</Badge>}
            <button className="btn-ghost btn-sm ml-auto" disabled={status.isPending} onClick={() => status.mutate(b)}>{b.status === 'ACTIVE' ? 'Mettre hors service' : 'Remettre en service'}</button>
          </div>
          <BoxSimulator boxId={b.id} keypad admin />
        </section>
      ))}

      <form className="card space-y-3" onSubmit={form.handleSubmit((v) => create.mutate(v))} noValidate>
        <h2>Ajouter un coffre</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Nom" error={form.formState.errors.name?.message}><input className="input" placeholder="Coffre Moto 02" {...form.register('name')} /></Field>
          <Field label="Numéro de série" error={form.formState.errors.serial?.message}><input className="input" placeholder="SFD-0002" {...form.register('serial')} /></Field>
          <Field label="Livreur"><select className="input" {...form.register('courierId')}><option value="">Aucun pour l'instant</option>{couriers.data?.filter((c) => c.boxId === null).map((c) => <option key={c.user.id} value={c.user.id}>{c.user.fullName}</option>)}</select></Field>
        </div>
        <div>
          <span className="label">Compartiments ({sizes.length}) — de 1 à 10</span>
          <div className="flex flex-wrap items-center gap-2">
            {sizes.map((s, i) => (
              <select key={i} className="input w-auto !py-1.5" value={s} onChange={(e) => setSizes((all) => all.map((x, j) => (j === i ? (e.target.value as SizeClass) : x)))} aria-label={`Taille du compartiment ${i + 1}`}>
                {(['SMALL', 'MEDIUM', 'LARGE'] as SizeClass[]).map((x) => <option key={x} value={x}>{i + 1} · {sizeLabel[x]}</option>)}
              </select>
            ))}
            <button type="button" className="btn-ghost btn-sm" disabled={sizes.length >= 10} onClick={() => setSizes((all) => [...all, 'MEDIUM'])}>+ Ajouter</button>
            <button type="button" className="btn-ghost btn-sm" disabled={sizes.length <= 1} onClick={() => setSizes((all) => all.slice(0, -1))}>− Retirer</button>
          </div>
        </div>
        <button className="btn-primary" disabled={create.isPending}>Créer le coffre</button>
        {secret && (
          <div className="rounded-xl border border-amber-300 bg-amber-50 p-3 text-sm">
            <p className="font-semibold">Identifiants de l'appareil du {secret.name} — affichés une seule fois</p>
            <p className="font-mono text-xs">Identifiant : {secret.uid}<br />Secret : {secret.secret}</p>
            <p className="text-xs text-ink-500">À programmer dans le contrôleur ESP32 lors du passage au matériel physique.</p>
          </div>
        )}
      </form>
    </div>
  );
}

// ---------------------------------------------------------------- Evenements

interface OpeningEvent { id: number; boxId: number; compartmentId: number | null; orderId: number | null; type: string; success: boolean; detail: string | null; createdAt: string }
interface SensorEvent { id: number; deviceId: number; compartmentNo: number | null; type: string; value: string | null; createdAt: string }
interface SecurityEvent { id: number; type: string; userId: number | null; boxId: number | null; detail: string | null; createdAt: string }

export function AdminEventsPage() {
  const [tab, setTab] = useState<'openings' | 'sensors' | 'security'>('openings');
  const openings = useQuery({ queryKey: ['admin', 'events', 'openings'], queryFn: () => get<OpeningEvent[]>('/admin/events/openings'), enabled: tab === 'openings', refetchInterval: 10000 });
  const sensors = useQuery({ queryKey: ['admin', 'events', 'sensors'], queryFn: () => get<SensorEvent[]>('/admin/events/sensors'), enabled: tab === 'sensors', refetchInterval: 10000 });
  const security = useQuery({ queryKey: ['admin', 'events', 'security'], queryFn: () => get<SecurityEvent[]>('/admin/events/security'), enabled: tab === 'security', refetchInterval: 10000 });
  const tabs = [{ k: 'openings', l: 'Ouvertures et codes' }, { k: 'sensors', l: 'Capteurs et connexion' }, { k: 'security', l: 'Incidents de sécurité' }] as const;
  const current = tab === 'openings' ? openings : tab === 'sensors' ? sensors : security;
  return (
    <div>
      <PageTitle title="Événements électroniques et incidents" subtitle="Chaque ouverture, fermeture, tentative et anomalie est enregistrée." />
      <div className="mb-3 flex flex-wrap gap-2">{tabs.map((t) => <button key={t.k} className={tab === t.k ? 'btn-dark btn-sm' : 'btn-ghost btn-sm'} onClick={() => setTab(t.k)}>{t.l}</button>)}</div>
      {current.isLoading && <Spinner />}
      {current.isError && <ErrorBox error={current.error} retry={() => current.refetch()} />}
      <div className="card overflow-x-auto">
        {tab === 'openings' && <table className="table"><thead><tr><th>Date</th><th>Coffre</th><th>Événement</th><th>Commande</th><th>Résultat</th><th>Détail</th></tr></thead><tbody>{openings.data?.map((e) => <tr key={e.id}><td>{dateTime(e.createdAt)}</td><td>{e.boxId}</td><td className="font-mono text-xs">{e.type}</td><td>{e.orderId ? `n°${e.orderId}` : '—'}</td><td><Badge tone={e.success ? 'green' : 'red'}>{e.success ? 'OK' : 'Refus / échec'}</Badge></td><td>{e.detail ?? ''}</td></tr>)}</tbody></table>}
        {tab === 'sensors' && <table className="table"><thead><tr><th>Date</th><th>Appareil</th><th>Compartiment</th><th>Événement</th><th>Valeur</th></tr></thead><tbody>{sensors.data?.map((e) => <tr key={e.id}><td>{dateTime(e.createdAt)}</td><td>{e.deviceId}</td><td>{e.compartmentNo ?? '—'}</td><td className="font-mono text-xs">{e.type}</td><td>{e.value ?? ''}</td></tr>)}</tbody></table>}
        {tab === 'security' && <table className="table"><thead><tr><th>Date</th><th>Type</th><th>Compte</th><th>Coffre</th><th>Détail</th></tr></thead><tbody>{security.data?.map((e) => <tr key={e.id}><td>{dateTime(e.createdAt)}</td><td className="font-mono text-xs">{e.type}</td><td>{e.userId ?? '—'}</td><td>{e.boxId ?? '—'}</td><td>{e.detail ?? ''}</td></tr>)}</tbody></table>}
        {current.data?.length === 0 && <p className="py-4 text-center text-sm text-ink-500">Aucun événement.</p>}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Reclamations

export function AdminComplaintsPage() {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['admin', 'complaints'], queryFn: () => get<Complaint[]>('/admin/complaints') });
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const update = useMutation({ mutationFn: ({ c, status }: { c: Complaint; status: Complaint['status'] }) => put(`/admin/complaints/${c.id}`, { status, response: answers[c.id] ?? c.response ?? '' }), onSuccess: () => { toast.success('Réclamation mise à jour.'); queryClient.invalidateQueries({ queryKey: ['admin'] }); }, onError: fail });
  if (list.isLoading) return <Spinner />;
  if (list.isError) return <ErrorBox error={list.error} retry={() => list.refetch()} />;
  const label = { OPEN: 'Ouverte', IN_PROGRESS: 'En cours', RESOLVED: 'Résolue' };
  return (
    <div>
      <PageTitle title="Réclamations" />
      {list.data?.length === 0 && <Empty title="Aucune réclamation" />}
      <div className="space-y-3">
        {list.data?.map((c) => (
          <div key={c.id} className="card space-y-2 text-sm">
            <div className="flex items-center justify-between gap-2"><p className="font-semibold">{c.subject}</p><Badge tone={c.status === 'RESOLVED' ? 'green' : 'orange'}>{label[c.status]}</Badge></div>
            <p>{c.message}</p>
            <p className="text-xs text-ink-500">{dateTime(c.createdAt)} · compte n°{c.userId}{c.orderId ? ` · commande n°${c.orderId}` : ''}</p>
            <textarea className="input min-h-[60px]" placeholder="Réponse au client" value={answers[c.id] ?? c.response ?? ''} onChange={(e) => setAnswers((a) => ({ ...a, [c.id]: e.target.value }))} maxLength={2000} />
            <div className="flex gap-2">
              <button className="btn-ghost btn-sm" disabled={update.isPending} onClick={() => update.mutate({ c, status: 'IN_PROGRESS' })}>Marquer en cours</button>
              <button className="btn-primary btn-sm" disabled={update.isPending} onClick={() => update.mutate({ c, status: 'RESOLVED' })}>Répondre et résoudre</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Superadministrateur

const adminSchema = z.object({ fullName: z.string().trim().min(2, 'Nom obligatoire.'), email: z.string().email('Email invalide.'), phone: z.string().regex(/^\+?[0-9]{8,15}$/, '8 à 15 chiffres.'), password: z.string().min(10, '10 caractères minimum.').max(72) });
interface AuditLog { id: number; actorId: number | null; action: string; entityType: string | null; entityId: number | null; detail: string | null; createdAt: string }

export function SuperAdminPage() {
  const queryClient = useQueryClient();
  const admins = useQuery({ queryKey: ['admin', 'admins'], queryFn: () => get<User[]>('/superadmin/admins') });
  const settings = useQuery({ queryKey: ['admin', 'settings'], queryFn: () => get<Record<string, number | boolean>>('/superadmin/settings') });
  const logs = useQuery({ queryKey: ['admin', 'audit'], queryFn: () => get<AuditLog[]>('/superadmin/audit-logs') });
  const form = useForm<z.infer<typeof adminSchema>>({ resolver: zodResolver(adminSchema) });
  const create = useMutation({ mutationFn: (v: z.infer<typeof adminSchema>) => post('/superadmin/admins', v), onSuccess: () => { toast.success('Administrateur créé.'); form.reset(); queryClient.invalidateQueries({ queryKey: ['admin'] }); }, onError: fail });
  const revoke = useMutation({ mutationFn: (id: number) => put(`/superadmin/users/${id}/admin`, { admin: false }), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin'] }), onError: fail });
  const settingLabel: Record<string, string> = { deliveryFee: 'Frais de livraison (FCFA)', commissionPercent: 'Commission (%)', courierSharePercent: 'Part du livreur sur les frais (%)', codeValidityMinutes: 'Validité du code (min)', codeMaxAttempts: 'Codes incorrects avant blocage', codeLockMinutes: 'Blocage du clavier (min)', loadingAuthorizationMinutes: 'Autorisation de chargement (min)', simulatorEnabled: 'Coffre simulé', paymentsSandboxEnabled: 'Paiements de test' };
  return (
    <div className="space-y-4">
      <PageTitle title="Superadministration" />
      <div className="grid gap-4 lg:grid-cols-2">
        <form className="card space-y-3" onSubmit={form.handleSubmit((v) => create.mutate(v))} noValidate>
          <h2>Créer un administrateur</h2>
          <Field label="Nom" error={form.formState.errors.fullName?.message}><input className="input" {...form.register('fullName')} /></Field>
          <Field label="Email" error={form.formState.errors.email?.message}><input className="input" type="email" {...form.register('email')} /></Field>
          <Field label="Téléphone" error={form.formState.errors.phone?.message}><input className="input" type="tel" {...form.register('phone')} /></Field>
          <Field label="Mot de passe initial" error={form.formState.errors.password?.message}><input className="input" type="password" autoComplete="new-password" {...form.register('password')} /></Field>
          <button className="btn-primary" disabled={create.isPending}>Créer</button>
        </form>
        <div className="space-y-4">
          <div className="card space-y-2"><h2>Administrateurs</h2>
            {admins.data?.length === 0 && <p className="text-sm text-ink-500">Aucun administrateur.</p>}
            {admins.data?.map((a) => <div key={a.id} className="flex items-center justify-between gap-2 text-sm"><span>{a.fullName} — {a.email}</span>{a.roles.length > 1 ? <button className="btn-danger btn-sm" disabled={revoke.isPending} onClick={() => revoke.mutate(a.id)}>Retirer la permission</button> : <span className="text-xs text-ink-500">Suspension depuis « Utilisateurs »</span>}</div>)}
          </div>
          <div className="card space-y-1 text-sm"><h2>Paramètres généraux</h2>
            {settings.data && Object.entries(settings.data).map(([k, v]) => <div key={k} className="flex justify-between border-t border-ink-100 py-1"><span className="text-ink-500">{settingLabel[k] ?? k}</span><span className="font-semibold">{typeof v === 'boolean' ? (v ? 'Activé' : 'Désactivé') : v}</span></div>)}
            <p className="pt-1 text-xs text-ink-500">Ces paramètres se règlent par variables d'environnement du serveur (voir le README), puis redémarrage.</p>
          </div>
        </div>
      </div>
      <div className="card overflow-x-auto"><h2 className="mb-2">Journal d'audit</h2>
        {logs.isLoading && <Spinner />}
        <table className="table"><thead><tr><th>Date</th><th>Acteur</th><th>Action</th><th>Objet</th><th>Détail</th></tr></thead>
          <tbody>{logs.data?.map((l) => <tr key={l.id}><td>{dateTime(l.createdAt)}</td><td>{l.actorId ?? 'système'}</td><td className="font-mono text-xs">{l.action}</td><td>{l.entityType ? `${l.entityType} ${l.entityId ?? ''}` : '—'}</td><td>{l.detail ?? ''}</td></tr>)}</tbody></table>
      </div>
    </div>
  );
}
