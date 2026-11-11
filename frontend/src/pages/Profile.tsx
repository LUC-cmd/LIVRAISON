import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MapPin, Trash2 } from 'lucide-react';
import { del, errorMessage, get, post, put } from '../api/client';
import { roleLabel } from '../api/labels';
import type { Address, User } from '../api/types';
import { MapView } from '../components/MapView';
import { Badge, ErrorBox, Field, PageTitle, Spinner } from '../components/ui';
import { hasRole, useSession } from '../stores/session';
import { toast } from '../stores/toast';

const profileSchema = z.object({ fullName: z.string().trim().min(2, 'Nom obligatoire.').max(120), phone: z.string().regex(/^\+?[0-9]{8,15}$/, '8 à 15 chiffres.') });
const passwordSchema = z.object({ currentPassword: z.string().min(1, 'Obligatoire.'), newPassword: z.string().min(8, '8 caractères minimum.').max(72) });
const addressSchema = z.object({ label: z.string().trim().min(1, 'Libellé obligatoire.').max(60), line: z.string().trim().min(3, 'Adresse obligatoire.').max(255), city: z.string().trim().min(2, 'Ville obligatoire.').max(80) });
interface Prefs { inApp: boolean; email: boolean; sms: boolean; push: boolean }

function Addresses() {
  const queryClient = useQueryClient();
  const list = useQuery({ queryKey: ['addresses'], queryFn: () => get<Address[]>('/me/addresses') });
  const [position, setPosition] = useState<{ lat: number; lng: number } | null>(null);
  const form = useForm<z.infer<typeof addressSchema>>({ resolver: zodResolver(addressSchema), defaultValues: { city: 'Lomé' } });
  const done = () => queryClient.invalidateQueries({ queryKey: ['addresses'] });
  const create = useMutation({
    mutationFn: (v: z.infer<typeof addressSchema>) => post<Address>('/me/addresses', { ...v, latitude: position!.lat, longitude: position!.lng, defaultAddress: false }),
    onSuccess: () => { toast.success('Adresse ajoutée.'); form.reset({ city: 'Lomé', label: '', line: '' }); setPosition(null); done(); },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const makeDefault = useMutation({ mutationFn: (a: Address) => put(`/me/addresses/${a.id}`, { ...a, defaultAddress: true }), onSuccess: done, onError: (e) => toast.error(errorMessage(e)) });
  const remove = useMutation({ mutationFn: (id: number) => del(`/me/addresses/${id}`), onSuccess: done, onError: (e) => toast.error(errorMessage(e)) });
  const locate = () => {
    if (!navigator.geolocation) { toast.error("La géolocalisation n'est pas disponible sur cet appareil."); return; }
    navigator.geolocation.getCurrentPosition((p) => setPosition({ lat: p.coords.latitude, lng: p.coords.longitude }), () => toast.error('Position refusée ou indisponible. Touchez la carte pour placer le repère.'), { enableHighAccuracy: true, timeout: 10000 });
  };
  return (
    <div className="card space-y-3">
      <h2>Adresses de livraison</h2>
      {list.isLoading && <Spinner />}
      {list.isError && <ErrorBox error={list.error} retry={() => list.refetch()} />}
      {list.data?.length === 0 && <p className="text-sm text-ink-500">Aucune adresse. Ajoutez-en une pour commander.</p>}
      <ul className="space-y-2">
        {list.data?.map((a) => (
          <li key={a.id} className="flex flex-wrap items-center gap-2 rounded-xl border border-ink-100 px-3 py-2 text-sm">
            <MapPin className="h-4 w-4 text-brand-500" />
            <span className="font-semibold">{a.label}</span><span className="text-ink-500">{a.line}, {a.city}</span>
            {a.defaultAddress ? <Badge tone="green">Par défaut</Badge> : <button className="text-xs font-semibold text-brand-600" onClick={() => makeDefault.mutate(a)}>Définir par défaut</button>}
            <button className="ml-auto text-red-600" onClick={() => remove.mutate(a.id)} aria-label={`Supprimer ${a.label}`}><Trash2 className="h-4 w-4" /></button>
          </li>
        ))}
      </ul>
      <form className="space-y-3 border-t border-ink-100 pt-3" onSubmit={form.handleSubmit((v) => { if (!position) { toast.error('Placez le repère sur la carte.'); return; } create.mutate(v); })} noValidate>
        <p className="text-sm font-semibold">Nouvelle adresse</p>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Libellé" error={form.formState.errors.label?.message}><input className="input" placeholder="Maison" {...form.register('label')} /></Field>
          <Field label="Adresse" error={form.formState.errors.line?.message}><input className="input" placeholder="Rue, quartier, repère" {...form.register('line')} /></Field>
          <Field label="Ville" error={form.formState.errors.city?.message}><input className="input" {...form.register('city')} /></Field>
        </div>
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs text-ink-500">{position ? `Repère : ${position.lat.toFixed(5)}, ${position.lng.toFixed(5)}` : 'Touchez la carte pour placer le point de livraison.'}</p>
          <button type="button" className="btn-ghost btn-sm" onClick={locate}>Utiliser ma position</button>
        </div>
        <MapView height={240} points={position ? [{ ...position, label: 'Point de livraison', kind: 'destination' }] : []} onPick={(lat, lng) => setPosition({ lat, lng })} />
        <button className="btn-primary" disabled={create.isPending}>Ajouter l'adresse</button>
      </form>
    </div>
  );
}

export function ProfilePage() {
  const { user, setUser } = useSession();
  const queryClient = useQueryClient();
  const profile = useForm<z.infer<typeof profileSchema>>({ resolver: zodResolver(profileSchema), defaultValues: { fullName: user?.fullName, phone: user?.phone } });
  const password = useForm<z.infer<typeof passwordSchema>>({ resolver: zodResolver(passwordSchema) });
  const save = useMutation({ mutationFn: (v: z.infer<typeof profileSchema>) => put<User>('/me', v), onSuccess: (u) => { setUser(u); toast.success('Profil enregistré.'); }, onError: (e) => toast.error(errorMessage(e)) });
  const change = useMutation({ mutationFn: (v: z.infer<typeof passwordSchema>) => put('/me/password', v), onSuccess: () => { password.reset(); toast.success('Mot de passe modifié.'); }, onError: (e) => toast.error(errorMessage(e)) });
  const prefs = useQuery({ queryKey: ['prefs'], queryFn: () => get<Prefs>('/me/notification-preferences') });
  const savePrefs = useMutation({ mutationFn: (p: Prefs) => put<Prefs>('/me/notification-preferences', p), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prefs'] }), onError: (e) => toast.error(errorMessage(e)) });
  if (!user) return null;
  return (
    <div className="space-y-4">
      <PageTitle title="Mon profil" subtitle={`${user.email} · ${user.roles.map((r) => roleLabel[r]).join(', ')}`} />
      <div className="grid gap-4 md:grid-cols-2">
        <form className="card space-y-3" onSubmit={profile.handleSubmit((v) => save.mutate(v))} noValidate>
          <h2>Informations</h2>
          <Field label="Nom complet" error={profile.formState.errors.fullName?.message}><input className="input" {...profile.register('fullName')} /></Field>
          <Field label="Téléphone" error={profile.formState.errors.phone?.message}><input className="input" type="tel" {...profile.register('phone')} /></Field>
          <button className="btn-primary" disabled={save.isPending}>Enregistrer</button>
        </form>
        <form className="card space-y-3" onSubmit={password.handleSubmit((v) => change.mutate(v))} noValidate>
          <h2>Mot de passe</h2>
          <Field label="Mot de passe actuel" error={password.formState.errors.currentPassword?.message}><input className="input" type="password" autoComplete="current-password" {...password.register('currentPassword')} /></Field>
          <Field label="Nouveau mot de passe" error={password.formState.errors.newPassword?.message}><input className="input" type="password" autoComplete="new-password" {...password.register('newPassword')} /></Field>
          <button className="btn-dark" disabled={change.isPending}>Modifier</button>
        </form>
      </div>
      {prefs.data && (
        <div className="card space-y-2">
          <h2>Notifications</h2>
          <p className="text-xs text-ink-500">Les notifications dans l'application sont toujours actives. L'email est envoyé si un serveur d'envoi est configuré sur la plateforme.</p>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={prefs.data.email} disabled={savePrefs.isPending} onChange={(e) => savePrefs.mutate({ ...prefs.data!, email: e.target.checked })} /> Recevoir aussi les notifications par email
          </label>
        </div>
      )}
      {hasRole(user, 'CUSTOMER') && <Addresses />}
    </div>
  );
}
