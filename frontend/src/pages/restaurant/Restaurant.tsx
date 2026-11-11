import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BarChart3, ChefHat, ClipboardList, HandCoins, ImagePlus, Pencil, Percent, Store, Timer, Trash2, Wallet, X } from 'lucide-react';
import { del, errorMessage, get, patch, post, put, uploadImage } from '../../api/client';
import { compartmentStateLabel, dateTime, dayLabel, fcfa, orderStatusLabel, sizeLabel } from '../../api/labels';
import type { BoxView, Category, DayStat, Dish, Hour, OrderStatus, RestaurantOrder, RestaurantProfile, SizeClass } from '../../api/types';
import { BoxSimulator } from '../../components/BoxSimulator';
import { MapView } from '../../components/MapView';
import { DishArt } from '../../components/DishArt';
import { Badge, BarChart, Empty, ErrorBox, Field, PageTitle, Spinner, Stat } from '../../components/ui';
import { toast } from '../../stores/toast';

const isNotFound = (e: unknown) => axios.isAxiosError(e) && e.response?.status === 404;
const useProfile = () => useQuery({ queryKey: ['restaurant', 'profile'], queryFn: () => get<RestaurantProfile>('/restaurant/profile'), retry: false });

function NeedsProfile() {
  return <Empty title="Créez d'abord votre restaurant" hint="Renseignez le profil de votre restaurant dans l'onglet Profil. Il sera visible des clients après validation par un administrateur." />;
}

// ---------------------------------------------------------------- Profil

const profileSchema = z.object({
  name: z.string().trim().min(2, 'Nom obligatoire.').max(120), description: z.string().max(1000), cuisine: z.string().max(60),
  addressLine: z.string().trim().min(3, 'Adresse obligatoire.').max(255), city: z.string().trim().min(2, 'Ville obligatoire.').max(80),
  phone: z.string().regex(/^\+?[0-9]{8,15}$/, '8 à 15 chiffres.'), accepting: z.boolean(),
});
type ProfileForm = z.infer<typeof profileSchema>;

export function RestaurantProfilePage() {
  const queryClient = useQueryClient();
  const profile = useProfile();
  const hours = useQuery({ queryKey: ['restaurant', 'hours'], queryFn: () => get<Hour[]>('/restaurant/hours'), enabled: !!profile.data });
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const [logoKey, setLogoKey] = useState<string | null>(null);
  const [logoUrl, setLogoUrl] = useState<string | null>(null);
  const [week, setWeek] = useState<Record<number, { on: boolean; opensAt: string; closesAt: string }>>({});
  const form = useForm<ProfileForm>({ resolver: zodResolver(profileSchema), defaultValues: { name: '', description: '', cuisine: '', addressLine: '', city: 'Lomé', phone: '', accepting: true } });

  useEffect(() => {
    if (!profile.data) return;
    const r = profile.data.restaurant;
    form.reset({ name: r.name, description: r.description, cuisine: r.cuisine, addressLine: r.addressLine, city: r.city, phone: r.phone, accepting: r.accepting });
    setPosition({ lat: r.latitude, lng: r.longitude });
    setLogoKey(r.logoKey);
    setLogoUrl(profile.data.logoUrl);
  }, [profile.data, form]);
  useEffect(() => {
    const next: typeof week = {};
    for (let d = 1; d <= 7; d++) {
      const h = hours.data?.find((x) => x.dayOfWeek === d);
      next[d] = { on: !!h, opensAt: h?.opensAt.slice(0, 5) ?? '08:00', closesAt: h?.closesAt.slice(0, 5) ?? '22:00' };
    }
    setWeek(next);
  }, [hours.data]);

  const save = useMutation({
    mutationFn: (v: ProfileForm) => put<RestaurantProfile>('/restaurant/profile', { ...v, logoKey, latitude: position!.lat, longitude: position!.lng }),
    onSuccess: () => { toast.success('Profil enregistré.'); queryClient.invalidateQueries({ queryKey: ['restaurant'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const saveHours = useMutation({
    mutationFn: () => put('/restaurant/hours', Object.entries(week).filter(([, v]) => v.on).map(([d, v]) => ({ dayOfWeek: Number(d), opensAt: `${v.opensAt}:00`, closesAt: `${v.closesAt}:00` }))),
    onSuccess: () => { toast.success('Horaires enregistrés.'); queryClient.invalidateQueries({ queryKey: ['restaurant'] }); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const upload = useMutation({
    mutationFn: (file: File) => uploadImage('logos', file),
    onSuccess: (r) => { setLogoKey(r.key); setLogoUrl(r.url); toast.info('Logo chargé. Enregistrez le profil pour le publier.'); },
    onError: (e) => toast.error(errorMessage(e)),
  });

  if (profile.isLoading) return <Spinner />;
  if (profile.isError && !isNotFound(profile.error)) return <ErrorBox error={profile.error} retry={() => profile.refetch()} />;
  const status = profile.data?.restaurant.status;
  return (
    <div className="space-y-4">
      <PageTitle icon={<Store className="h-6 w-6" />} subtitle="Profil public, position et horaires de votre restaurant." title={profile.data ? profile.data.restaurant.name : 'Créer mon restaurant'} action={status && <Badge tone={status === 'APPROVED' ? 'green' : status === 'PENDING' ? 'orange' : 'red'}>{status === 'APPROVED' ? 'Validé' : status === 'PENDING' ? 'En attente de validation' : 'Suspendu'}</Badge>} />
      <form className="card space-y-3" onSubmit={form.handleSubmit((v) => { if (!position) { toast.error('Placez votre restaurant sur la carte.'); return; } save.mutate(v); })} noValidate>
        <div className="flex items-center gap-3">
          <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-2xl bg-brand-100 text-brand-600">{logoUrl ? <img src={logoUrl} alt="Logo" className="h-full w-full object-cover" /> : <ImagePlus className="h-6 w-6" />}</div>
          <label className="btn-ghost btn-sm cursor-pointer">{upload.isPending ? 'Envoi…' : 'Choisir un logo'}<input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = ''; }} /></label>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Nom" error={form.formState.errors.name?.message}><input className="input" {...form.register('name')} /></Field>
          <Field label="Cuisine" error={form.formState.errors.cuisine?.message}><input className="input" placeholder="Togolaise, grillades…" {...form.register('cuisine')} /></Field>
          <Field label="Adresse" error={form.formState.errors.addressLine?.message}><input className="input" {...form.register('addressLine')} /></Field>
          <Field label="Ville" error={form.formState.errors.city?.message}><input className="input" {...form.register('city')} /></Field>
          <Field label="Téléphone" error={form.formState.errors.phone?.message}><input className="input" type="tel" {...form.register('phone')} /></Field>
          <label className="flex items-center gap-2 self-end pb-2 text-sm"><input type="checkbox" {...form.register('accepting')} /> Accepter les commandes</label>
        </div>
        <Field label="Description" error={form.formState.errors.description?.message}><textarea className="input min-h-[80px]" {...form.register('description')} /></Field>
        <p className="text-xs text-ink-500">{position ? `Position : ${position.lat.toFixed(5)}, ${position.lng.toFixed(5)}` : 'Touchez la carte pour placer votre restaurant.'}</p>
        <MapView height={240} points={position ? [{ ...position, label: 'Votre restaurant', kind: 'pickup' }] : []} onPick={(lat, lng) => setPosition({ lat, lng })} />
        <button className="btn-primary" disabled={save.isPending}>{profile.data ? 'Enregistrer le profil' : 'Créer le restaurant'}</button>
      </form>
      {profile.data && (
        <div className="card space-y-3">
          <h2>Horaires d'ouverture</h2>
          <p className="text-xs text-ink-500">Sans horaire coché, le restaurant est considéré comme ouvert en permanence.</p>
          {Object.entries(week).map(([d, v]) => (
            <div key={d} className="flex flex-wrap items-center gap-2 text-sm">
              <label className="flex w-28 items-center gap-2"><input type="checkbox" checked={v.on} onChange={(e) => setWeek((w) => ({ ...w, [d]: { ...v, on: e.target.checked } }))} /> {dayLabel[Number(d)]}</label>
              <input type="time" className="input w-auto !py-1.5" disabled={!v.on} value={v.opensAt} onChange={(e) => setWeek((w) => ({ ...w, [d]: { ...v, opensAt: e.target.value } }))} aria-label={`Ouverture ${dayLabel[Number(d)]}`} />
              <span>–</span>
              <input type="time" className="input w-auto !py-1.5" disabled={!v.on} value={v.closesAt} onChange={(e) => setWeek((w) => ({ ...w, [d]: { ...v, closesAt: e.target.value } }))} aria-label={`Fermeture ${dayLabel[Number(d)]}`} />
            </div>
          ))}
          <button className="btn-dark" disabled={saveHours.isPending} onClick={() => saveHours.mutate()}>Enregistrer les horaires</button>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------- Menu

const dishSchema = z.object({
  name: z.string().trim().min(2, 'Nom obligatoire.').max(120), description: z.string().max(600),
  price: z.coerce.number({ invalid_type_error: 'Prix obligatoire.' }).int('Prix entier en FCFA.').min(1, 'Prix positif.').max(10000000),
  sizeClass: z.enum(['SMALL', 'MEDIUM', 'LARGE']), categoryId: z.string(),
});
type DishForm = z.infer<typeof dishSchema>;
const emptyDish: DishForm = { name: '', description: '', price: 0, sizeClass: 'MEDIUM', categoryId: '' };

export function RestaurantMenuPage() {
  const queryClient = useQueryClient();
  const profile = useProfile();
  const ready = !!profile.data;
  const categories = useQuery({ queryKey: ['restaurant', 'categories'], queryFn: () => get<Category[]>('/restaurant/categories'), enabled: ready });
  const dishes = useQuery({ queryKey: ['restaurant', 'dishes'], queryFn: () => get<Dish[]>('/restaurant/dishes'), enabled: ready });
  const [editing, setEditing] = useState<Dish | null>(null);
  const [categoryName, setCategoryName] = useState('');
  const form = useForm<DishForm>({ resolver: zodResolver(dishSchema), defaultValues: emptyDish });
  const fileInput = useRef<HTMLInputElement>(null);
  const [imageFor, setImageFor] = useState<number | null>(null);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['restaurant'] });
  const fail = (e: unknown) => toast.error(errorMessage(e));

  const saveDish = useMutation({
    mutationFn: (v: DishForm) => {
      const body = { name: v.name, description: v.description, price: v.price, sizeClass: v.sizeClass as SizeClass, categoryId: v.categoryId ? Number(v.categoryId) : null };
      return editing ? put(`/restaurant/dishes/${editing.id}`, body) : post('/restaurant/dishes', body);
    },
    onSuccess: () => { toast.success(editing ? 'Plat modifié.' : 'Plat ajouté.'); setEditing(null); form.reset(emptyDish); refresh(); },
    onError: fail,
  });
  const availability = useMutation({ mutationFn: (d: Dish) => patch(`/restaurant/dishes/${d.id}/availability`, { available: !d.available }), onSuccess: refresh, onError: fail });
  const removeDish = useMutation({ mutationFn: (id: number) => del(`/restaurant/dishes/${id}`), onSuccess: () => { toast.success('Plat retiré du menu.'); refresh(); }, onError: fail });
  const addCategory = useMutation({ mutationFn: () => post('/restaurant/categories', { name: categoryName.trim(), position: categories.data?.length ?? 0 }), onSuccess: () => { setCategoryName(''); refresh(); }, onError: fail });
  const removeCategory = useMutation({ mutationFn: (id: number) => del(`/restaurant/categories/${id}`), onSuccess: refresh, onError: fail });
  const addImage = useMutation({
    mutationFn: async ({ dishId, file }: { dishId: number; file: File }) => { const up = await uploadImage('dishes', file); return post(`/restaurant/dishes/${dishId}/images`, { storageKey: up.key }); },
    onSuccess: () => { toast.success('Photo ajoutée.'); refresh(); }, onError: fail,
  });
  const removeImage = useMutation({ mutationFn: ({ dishId, imageId }: { dishId: number; imageId: number }) => del(`/restaurant/dishes/${dishId}/images/${imageId}`), onSuccess: refresh, onError: fail });

  if (profile.isLoading) return <Spinner />;
  if (!profile.data) return isNotFound(profile.error) ? <NeedsProfile /> : <ErrorBox error={profile.error} retry={() => profile.refetch()} />;
  const edit = (d: Dish) => { setEditing(d); form.reset({ name: d.name, description: d.description, price: d.price, sizeClass: d.sizeClass, categoryId: d.categoryId ? String(d.categoryId) : '' }); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const categoryNameOf = (id: number | null) => categories.data?.find((c) => c.id === id)?.name ?? 'Sans catégorie';

  return (
    <div className="space-y-4">
      <PageTitle icon={<ChefHat className="h-6 w-6" />} title="Menu" subtitle="Catégories, plats, prix, photos et disponibilités." />
      <input ref={fileInput} type="file" accept="image/jpeg,image/png,image/webp" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f && imageFor) addImage.mutate({ dishId: imageFor, file: f }); e.target.value = ''; }} />
      <div className="grid gap-4 lg:grid-cols-[1fr_320px]">
        <form className="card space-y-3" onSubmit={form.handleSubmit((v) => saveDish.mutate(v))} noValidate>
          <div className="flex items-center justify-between"><h2>{editing ? `Modifier « ${editing.name} »` : 'Ajouter un plat'}</h2>{editing && <button type="button" className="btn-ghost btn-sm" onClick={() => { setEditing(null); form.reset(emptyDish); }}><X className="h-3.5 w-3.5" /> Annuler</button>}</div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Nom" error={form.formState.errors.name?.message}><input className="input" {...form.register('name')} /></Field>
            <Field label="Prix (FCFA)" error={form.formState.errors.price?.message}><input className="input" type="number" min={1} step={50} {...form.register('price')} /></Field>
            <Field label="Catégorie"><select className="input" {...form.register('categoryId')}><option value="">Sans catégorie</option>{categories.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></Field>
            <Field label="Gabarit (taille du compartiment)"><select className="input" {...form.register('sizeClass')}>{(['SMALL', 'MEDIUM', 'LARGE'] as SizeClass[]).map((s) => <option key={s} value={s}>{sizeLabel[s]}</option>)}</select></Field>
          </div>
          <Field label="Description" error={form.formState.errors.description?.message}><textarea className="input min-h-[70px]" {...form.register('description')} /></Field>
          <button className="btn-primary" disabled={saveDish.isPending}>{editing ? 'Enregistrer' : 'Ajouter le plat'}</button>
        </form>
        <div className="card h-fit space-y-2">
          <h2>Catégories</h2>
          {categories.data?.map((c) => (
            <div key={c.id} className="flex items-center justify-between rounded-lg border border-ink-100 px-3 py-1.5 text-sm"><span>{c.name}</span><button className="text-red-600" onClick={() => removeCategory.mutate(c.id)} aria-label={`Supprimer ${c.name}`}><Trash2 className="h-4 w-4" /></button></div>
          ))}
          <div className="flex gap-2"><input className="input" placeholder="Nouvelle catégorie" value={categoryName} onChange={(e) => setCategoryName(e.target.value)} /><button className="btn-dark" disabled={!categoryName.trim() || addCategory.isPending} onClick={() => addCategory.mutate()}>Ajouter</button></div>
        </div>
      </div>
      {dishes.isLoading && <Spinner />}
      {dishes.data?.length === 0 && <Empty title="Aucun plat" hint="Ajoutez votre premier plat avec le formulaire ci-dessus." />}
      <div className="grid gap-3 md:grid-cols-2">
        {dishes.data?.map((d) => (
          <div key={d.id} className="card space-y-2">
            <div className="flex items-start justify-between gap-2">
              <div><p className="font-semibold">{d.name}</p><p className="text-xs text-ink-500">{categoryNameOf(d.categoryId)} · {sizeLabel[d.sizeClass]}</p></div>
              <span className="font-bold text-brand-600">{fcfa(d.price)}</span>
            </div>
            <p className="text-sm text-ink-500">{d.description}</p>
            <div className="flex flex-wrap gap-2">
              {d.images.length === 0 && <div className="h-16 w-16 overflow-hidden rounded-lg" title="Illustration par défaut : ajoutez votre propre photo"><DishArt name={d.name} className="h-full w-full" /></div>}
              {d.images.map((img) => (
                <div key={img.id} className="relative h-16 w-16 overflow-hidden rounded-lg">
                  <img src={img.url} alt="" className="h-full w-full object-cover" />
                  <button className="absolute right-0.5 top-0.5 rounded-full bg-white/90 p-0.5 text-red-600" onClick={() => removeImage.mutate({ dishId: d.id, imageId: img.id })} aria-label="Supprimer la photo"><X className="h-3 w-3" /></button>
                </div>
              ))}
              <button className="flex h-16 w-16 items-center justify-center rounded-lg border border-dashed border-ink-300 text-ink-500" disabled={addImage.isPending} onClick={() => { setImageFor(d.id); fileInput.current?.click(); }} aria-label="Ajouter une photo"><ImagePlus className="h-5 w-5" /></button>
            </div>
            <div className="flex flex-wrap items-center gap-2 border-t border-ink-100 pt-2">
              <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={d.available} disabled={availability.isPending} onChange={() => availability.mutate(d)} /> Disponible</label>
              <button className="btn-ghost btn-sm ml-auto" onClick={() => edit(d)}><Pencil className="h-3.5 w-3.5" /> Modifier</button>
              <button className="btn-danger btn-sm" disabled={removeDish.isPending} onClick={() => removeDish.mutate(d.id)}><Trash2 className="h-3.5 w-3.5" /> Retirer</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- Commandes

const groupsOf: { title: string; statuses: OrderStatus[] }[] = [
  { title: 'À accepter', statuses: ['PENDING'] }, { title: 'En préparation', statuses: ['ACCEPTED'] },
  { title: 'Prêtes — chargement dans le coffre', statuses: ['READY', 'RESERVED'] }, { title: 'Chargées et en livraison', statuses: ['LOADED', 'IN_TRANSIT', 'ARRIVED'] },
  { title: 'Terminées', statuses: ['COLLECTED', 'REJECTED', 'CANCELLED'] },
];

export function RestaurantOrdersPage() {
  const queryClient = useQueryClient();
  const profile = useProfile();
  const orders = useQuery({ queryKey: ['orders', 'restaurant'], queryFn: () => get<RestaurantOrder[]>('/restaurant/orders'), enabled: !!profile.data, refetchInterval: 15000 });
  const boxes = useQuery({ queryKey: ['boxes', 'list'], queryFn: () => get<BoxView[]>('/boxes'), enabled: !!profile.data, refetchInterval: 15000 });
  const [rejecting, setRejecting] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const refresh = () => { queryClient.invalidateQueries({ queryKey: ['orders'] }); queryClient.invalidateQueries({ queryKey: ['boxes'] }); queryClient.invalidateQueries({ queryKey: ['hardware'] }); };
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: number; action: string; body?: unknown }) => post<{ message?: string; compartmentReserved?: boolean }>(`/restaurant/orders/${id}/${action}`, body),
    onSuccess: (r) => { if (r && typeof r === 'object' && 'message' in r && r.message) (r.compartmentReserved ? toast.success : toast.info)(r.message); refresh(); },
    onError: (e) => { toast.error(errorMessage(e)); refresh(); },
  });

  if (profile.isLoading) return <Spinner />;
  if (!profile.data) return isNotFound(profile.error) ? <NeedsProfile /> : <ErrorBox error={profile.error} retry={() => profile.refetch()} />;
  if (orders.isLoading) return <Spinner />;
  if (orders.isError) return <ErrorBox error={orders.error} retry={() => orders.refetch()} />;
  const list = orders.data ?? [];
  const activeBoxIds = Array.from(new Set(list.filter((o) => o.order.status === 'RESERVED' && o.order.boxId).map((o) => o.order.boxId as number)));
  const free = (boxes.data ?? []).map((b) => `${b.name} : ${b.compartments.filter((c) => c.state === 'AVAILABLE').length} libre(s) sur ${b.compartments.length}${b.device?.online ? '' : ' (hors connexion)'}${b.courierAvailable ? '' : ' (livreur indisponible)'}`);

  return (
    <div className="space-y-5">
      <PageTitle icon={<ClipboardList className="h-6 w-6" />} title="Commandes" subtitle={free.length ? `Coffres en service — ${free.join(' · ')}` : 'Aucun coffre en service pour le moment.'} />
      {list.length === 0 && <Empty title="Aucune commande reçue" hint="Les nouvelles commandes apparaissent ici en temps réel." />}
      {groupsOf.map((g) => {
        const items = list.filter((o) => g.statuses.includes(o.order.status));
        if (items.length === 0) return null;
        return (
          <section key={g.title} className="space-y-2">
            <h2>{g.title} ({items.length})</h2>
            <div className="grid gap-3 md:grid-cols-2">
              {items.map(({ order: o, customerName, net }) => (
                <div key={o.id} className="card space-y-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-bold">n°{o.id} · {customerName}</p>
                    <Badge tone={o.status === 'COLLECTED' ? 'green' : o.status === 'REJECTED' || o.status === 'CANCELLED' ? 'red' : 'blue'}>{orderStatusLabel[o.status]}</Badge>
                  </div>
                  <ul className="text-sm">{o.items.map((i) => <li key={i.dishId}>{i.quantity} × {i.name}</li>)}</ul>
                  <p className="text-xs text-ink-500">{dateTime(o.createdAt)} · {fcfa(o.subtotal)} (net {fcfa(net)}) · gabarit {sizeLabel[o.requiredSize]}</p>
                  {o.compartmentNumber && <p className="rounded-lg bg-ink-50 px-3 py-2 text-sm"><span className="font-semibold">{o.boxName}</span> — compartiment {o.compartmentNumber} ({o.compartmentState ? compartmentStateLabel[o.compartmentState] : ''})</p>}
                  {o.status === 'PENDING' && (rejecting === o.id ? (
                    <div className="space-y-2">
                      <input className="input" placeholder="Motif du refus" value={reason} onChange={(e) => setReason(e.target.value)} />
                      <div className="flex gap-2">
                        <button className="btn-danger btn-sm" disabled={!reason.trim() || act.isPending} onClick={() => { act.mutate({ id: o.id, action: 'reject', body: { reason } }); setRejecting(null); setReason(''); }}>Confirmer le refus</button>
                        <button className="btn-ghost btn-sm" onClick={() => setRejecting(null)}>Retour</button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex gap-2">
                      <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'accept' })}>Accepter</button>
                      <button className="btn-danger btn-sm" onClick={() => { setRejecting(o.id); setReason(''); }}>Refuser</button>
                    </div>
                  ))}
                  {o.status === 'ACCEPTED' && <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'ready' })}>Repas prêt — chercher un compartiment</button>}
                  {o.status === 'READY' && <button className="btn-dark btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'assign-compartment' })}>Rechercher un compartiment</button>}
                  {o.status === 'RESERVED' && o.compartmentState === 'RESERVED' && <button className="btn-primary btn-sm" disabled={act.isPending} onClick={() => act.mutate({ id: o.id, action: 'loading-authorization' })}>Ouvrir le compartiment {o.compartmentNumber} pour charger</button>}
                  {o.status === 'RESERVED' && o.compartmentState === 'LOADING' && <p className="text-sm font-semibold text-brand-600">Compartiment ouvert : déposez le repas puis fermez la porte sur le coffre ci-dessous.</p>}
                </div>
              ))}
            </div>
          </section>
        );
      })}
      {activeBoxIds.map((id) => (
        <section key={id} className="space-y-2">
          <h2>Chargement du coffre</h2>
          <p className="text-sm text-ink-500">Prototype virtuel : sélectionnez le compartiment ouvert, déposez le repas puis fermez la porte. Le code client n'est généré qu'après vérification du verrouillage.</p>
          <BoxSimulator boxId={id} />
        </section>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------- Statistiques

interface Stats { totalOrders: number; collectedOrders: number; inProgressOrders: number; rejectedOrders: number; revenue: number; commission: number; net: number; lastDays: DayStat[]; topDishes: { name: string; quantity: number; revenue: number }[] }

export function RestaurantStatsPage() {
  const profile = useProfile();
  const stats = useQuery({ queryKey: ['restaurant', 'stats'], queryFn: () => get<Stats>('/restaurant/stats'), enabled: !!profile.data });
  if (profile.isLoading || stats.isLoading) return <Spinner />;
  if (!profile.data) return isNotFound(profile.error) ? <NeedsProfile /> : <ErrorBox error={profile.error} />;
  if (stats.isError || !stats.data) return <ErrorBox error={stats.error} retry={() => stats.refetch()} />;
  const s = stats.data;
  return (
    <div className="space-y-4">
      <PageTitle icon={<BarChart3 className="h-6 w-6" />} title="Ventes et statistiques" subtitle="Calculées sur les commandes retirées par les clients." />
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat icon={<Wallet className="h-5 w-5" />} label="Ventes" value={fcfa(s.revenue)} hint={`${s.collectedOrders} commande(s) retirée(s)`} />
        <Stat icon={<Percent className="h-5 w-5" />} tone="dark" label="Commission plateforme" value={fcfa(s.commission)} />
        <Stat icon={<HandCoins className="h-5 w-5" />} tone="green" label="Revenu net" value={fcfa(s.net)} />
        <Stat icon={<Timer className="h-5 w-5" />} tone="blue" label="En cours" value={s.inProgressOrders} hint={`${s.rejectedOrders} refusée(s) ou annulée(s)`} />
      </div>
      <div className="grid gap-4 lg:grid-cols-2">
        <div className="card"><h2 className="mb-3">Ventes des 7 derniers jours</h2><BarChart data={s.lastDays.map((d) => ({ label: d.day.slice(8, 10) + '/' + d.day.slice(5, 7), value: d.revenue }))} format={(n) => n.toLocaleString('fr-FR')} /></div>
        <div className="card"><h2 className="mb-3">Plats les plus vendus</h2>
          {s.topDishes.length === 0 ? <p className="text-sm text-ink-500">Aucune vente pour le moment.</p> : (
            <table className="table"><thead><tr><th>Plat</th><th>Quantité</th><th>Ventes</th></tr></thead><tbody>{s.topDishes.map((d) => <tr key={d.name}><td>{d.name}</td><td>{d.quantity}</td><td>{fcfa(d.revenue)}</td></tr>)}</tbody></table>
          )}
        </div>
      </div>
    </div>
  );
}
