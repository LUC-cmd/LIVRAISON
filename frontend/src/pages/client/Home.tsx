import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Clock, Lock, MapPin, Search, ShieldCheck } from 'lucide-react';
import { get } from '../../api/client';
import type { RestaurantCard } from '../../api/types';
import { Badge, Empty, ErrorBox, Spinner } from '../../components/ui';

export function HomePage() {
  const [q, setQ] = useState('');
  const [cuisine, setCuisine] = useState('');
  const [openOnly, setOpenOnly] = useState(false);
  const all = useQuery({ queryKey: ['restaurants'], queryFn: () => get<RestaurantCard[]>('/restaurants') });
  const cuisines = Array.from(new Set((all.data ?? []).map((r) => r.cuisine).filter(Boolean))).sort();
  const needle = q.trim().toLowerCase();
  const shown = (all.data ?? []).filter((r) =>
    (!needle || `${r.name} ${r.description} ${r.cuisine}`.toLowerCase().includes(needle)) && (!cuisine || r.cuisine === cuisine) && (!openOnly || r.openNow));

  return (
    <div className="space-y-6">
      <section className="overflow-hidden rounded-3xl bg-ink-900 px-6 py-8 text-white sm:px-10 sm:py-12">
        <p className="text-xs font-semibold uppercase tracking-widest text-brand-400">Livraison sécurisée à Lomé</p>
        <h1 className="mt-2 max-w-xl text-3xl font-black leading-tight sm:text-4xl">Votre repas voyage sous clé. Vous seul l'ouvrez.</h1>
        <p className="mt-3 max-w-xl text-sm text-ink-300">Chaque commande est verrouillée dans son propre compartiment. À l'arrivée, votre code ouvre uniquement le vôtre.</p>
        <div className="mt-5 flex flex-wrap gap-4 text-xs text-ink-300">
          <span className="inline-flex items-center gap-1.5"><Lock className="h-4 w-4 text-brand-400" /> Compartiment individuel</span>
          <span className="inline-flex items-center gap-1.5"><ShieldCheck className="h-4 w-4 text-brand-400" /> Code à usage unique</span>
          <span className="inline-flex items-center gap-1.5"><MapPin className="h-4 w-4 text-brand-400" /> Suivi du livreur sur la carte</span>
        </div>
      </section>

      <section className="space-y-3">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-3 h-4 w-4 text-ink-300" />
            <input className="input pl-9" placeholder="Rechercher un restaurant ou une cuisine" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Rechercher" />
          </div>
          <select className="input w-auto" value={cuisine} onChange={(e) => setCuisine(e.target.value)} aria-label="Type de cuisine">
            <option value="">Toutes les cuisines</option>
            {cuisines.map((c) => <option key={c}>{c}</option>)}
          </select>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} /> Ouverts maintenant</label>
        </div>

        {all.isLoading && <Spinner />}
        {all.isError && <ErrorBox error={all.error} retry={() => all.refetch()} />}
        {all.data && shown.length === 0 && <Empty title="Aucun restaurant ne correspond" hint="Modifiez votre recherche ou vos filtres." />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {shown.map((r) => (
            <Link key={r.id} to={`/restaurants/${r.id}`} className="card rise group block overflow-hidden !p-0 transition hover:-translate-y-0.5">
              <div className="flex h-28 items-center justify-center bg-gradient-to-br from-brand-100 to-brand-50">
                {r.logoUrl ? <img src={r.logoUrl} alt="" className="h-full w-full object-cover" /> : <span className="text-4xl font-black text-brand-500">{r.name.charAt(0)}</span>}
              </div>
              <div className="space-y-1.5 p-4">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-bold group-hover:text-brand-600">{r.name}</p>
                  <Badge tone={r.openNow ? 'green' : 'gray'}><Clock className="h-3 w-3" /> {r.openNow ? 'Ouvert' : 'Fermé'}</Badge>
                </div>
                <p className="line-clamp-2 text-sm text-ink-500">{r.description}</p>
                <p className="flex items-center gap-1 text-xs text-ink-500"><MapPin className="h-3 w-3" /> {r.addressLine}, {r.city}{r.cuisine && ` · ${r.cuisine}`}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
