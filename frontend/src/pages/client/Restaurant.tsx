import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Minus, Plus, ShoppingBag } from 'lucide-react';
import { get } from '../../api/client';
import { dayLabel, fcfa, sizeLabel } from '../../api/labels';
import type { Dish, RestaurantDetail } from '../../api/types';
import { Badge, ErrorBox, Spinner } from '../../components/ui';
import { cartCount, cartSubtotal, useCart } from '../../stores/cart';

function DishCard({ dish, restaurantName, canOrder }: { dish: Dish; restaurantName: string; canOrder: boolean }) {
  const { lines, add, setQuantity } = useCart();
  const quantity = lines.find((l) => l.dishId === dish.id)?.quantity ?? 0;
  return (
    <div className="card flex gap-3">
      <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-brand-50 text-2xl">
        {dish.images[0] ? <img src={dish.images[0].url} alt={dish.name} className="h-full w-full object-cover" /> : '🍲'}
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{dish.name}</p>
        <p className="line-clamp-2 text-sm text-ink-500">{dish.description}</p>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
          <span className="font-bold text-brand-600">{fcfa(dish.price)}</span>
          {!dish.available ? <Badge>Indisponible</Badge> : !canOrder ? <Badge>Restaurant fermé</Badge> : quantity === 0 ? (
            <button className="btn-primary btn-sm" onClick={() => add(dish, restaurantName)}><Plus className="h-3.5 w-3.5" /> Ajouter</button>
          ) : (
            <div className="flex items-center gap-2">
              <button className="btn-ghost btn-sm" onClick={() => setQuantity(dish.id, quantity - 1)} aria-label="Retirer un"><Minus className="h-3.5 w-3.5" /></button>
              <span className="w-6 text-center text-sm font-bold">{quantity}</span>
              <button className="btn-ghost btn-sm" onClick={() => setQuantity(dish.id, quantity + 1)} aria-label="Ajouter un"><Plus className="h-3.5 w-3.5" /></button>
            </div>
          )}
        </div>
        <p className="mt-1 text-[11px] text-ink-300">Gabarit : {sizeLabel[dish.sizeClass]}</p>
      </div>
    </div>
  );
}

export function RestaurantPage() {
  const { id } = useParams();
  const detail = useQuery({ queryKey: ['restaurants', id], queryFn: () => get<RestaurantDetail>(`/restaurants/${id}`) });
  const lines = useCart((s) => s.lines);
  if (detail.isLoading) return <Spinner />;
  if (detail.isError || !detail.data) return <ErrorBox error={detail.error} retry={() => detail.refetch()} />;
  const { restaurant: r, categories, uncategorized, hours } = detail.data;
  const sections = [...categories.filter((c) => c.dishes.length > 0).map((c) => ({ name: c.name, dishes: c.dishes })), ...(uncategorized.length ? [{ name: 'Autres plats', dishes: uncategorized }] : [])];
  return (
    <div className="space-y-5 pb-24">
      <Link to="/" className="inline-flex items-center gap-1 text-sm font-semibold text-ink-500 hover:text-ink-900"><ArrowLeft className="h-4 w-4" /> Tous les restaurants</Link>
      <div className="card flex flex-wrap items-center gap-4">
        <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-2xl bg-brand-100 text-2xl font-black text-brand-600">
          {r.logoUrl ? <img src={r.logoUrl} alt="" className="h-full w-full object-cover" /> : r.name.charAt(0)}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2"><h1>{r.name}</h1><Badge tone={r.openNow ? 'green' : 'gray'}>{r.openNow ? 'Ouvert' : 'Fermé'}</Badge></div>
          <p className="text-sm text-ink-500">{r.description}</p>
          <p className="text-xs text-ink-500">{r.addressLine}, {r.city} · {r.phone}</p>
        </div>
        {hours.length > 0 && (
          <details className="text-xs text-ink-500">
            <summary className="cursor-pointer font-semibold">Horaires</summary>
            <ul className="mt-1">{hours.map((h) => <li key={h.dayOfWeek}>{dayLabel[h.dayOfWeek]} : {h.opensAt.slice(0, 5)} – {h.closesAt.slice(0, 5)}</li>)}</ul>
          </details>
        )}
      </div>
      {sections.length === 0 && <p className="text-sm text-ink-500">Ce restaurant n'a pas encore publié de plats.</p>}
      {sections.map((s) => (
        <section key={s.name} className="space-y-2">
          <h2>{s.name}</h2>
          <div className="grid gap-3 md:grid-cols-2">{s.dishes.map((d) => <DishCard key={d.id} dish={d} restaurantName={r.name} canOrder={r.openNow} />)}</div>
        </section>
      ))}
      {lines.length > 0 && (
        <Link to="/panier" className="btn-primary fixed inset-x-4 bottom-4 z-[1000] mx-auto max-w-md justify-between !py-3.5">
          <span className="inline-flex items-center gap-2"><ShoppingBag className="h-4 w-4" /> Voir le panier ({cartCount(lines)})</span>
          <span>{fcfa(cartSubtotal(lines))}</span>
        </Link>
      )}
    </div>
  );
}
