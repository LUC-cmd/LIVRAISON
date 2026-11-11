import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Minus, Plus, Trash2 } from 'lucide-react';
import { errorMessage, get, post } from '../../api/client';
import { fcfa, paymentMethodLabel } from '../../api/labels';
import type { Address, AppConfig, GroupView, PaymentMethod } from '../../api/types';
import { Empty, PageTitle } from '../../components/ui';
import { cartSubtotal, useCart } from '../../stores/cart';
import { hasRole, useSession } from '../../stores/session';
import { toast } from '../../stores/toast';

/** crypto.randomUUID n'existe qu'en contexte securise (HTTPS ou localhost) : repli pour les tests sur telephone en HTTP local. */
const newKey = () => (typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`);

export function CartPage() {
  const { lines, setQuantity, clear } = useCart();
  const user = useSession((s) => s.user);
  const isCustomer = hasRole(user, 'CUSTOMER');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const config = useQuery({ queryKey: ['config'], queryFn: () => get<AppConfig>('/config') });
  const addresses = useQuery({ queryKey: ['addresses'], queryFn: () => get<Address[]>('/me/addresses'), enabled: isCustomer });
  const [addressId, setAddressId] = useState<number | null>(null);
  const [method, setMethod] = useState<PaymentMethod>('CASH_ON_DELIVERY');
  // Une cle par tentative de commande : un double clic ou un renvoi reseau ne cree pas deux commandes.
  const [idempotencyKey, setIdempotencyKey] = useState(newKey);
  const chosen = addressId ?? addresses.data?.find((a) => a.defaultAddress)?.id ?? addresses.data?.[0]?.id ?? null;

  const byRestaurant = useMemo(() => {
    const map = new Map<number, { name: string; lines: typeof lines }>();
    for (const l of lines) {
      if (!map.has(l.restaurantId)) map.set(l.restaurantId, { name: l.restaurantName, lines: [] });
      map.get(l.restaurantId)!.lines.push(l);
    }
    return [...map.entries()];
  }, [lines]);
  const subtotal = cartSubtotal(lines);
  const fee = config.data?.deliveryFee ?? 0;

  const checkout = useMutation({
    mutationFn: () => post<GroupView>('/customer/orders', { addressId: chosen, paymentMethod: method, items: lines.map((l) => ({ dishId: l.dishId, quantity: l.quantity })) }, { 'Idempotency-Key': idempotencyKey }),
    onSuccess: (group) => {
      clear();
      setIdempotencyKey(newKey());
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      toast.success('Commande enregistrée.');
      navigate(`/commandes/${group.id}`);
    },
    onError: (e) => { toast.error(errorMessage(e)); setIdempotencyKey(newKey()); },
  });

  if (lines.length === 0) return <Empty title="Votre panier est vide" hint="Ajoutez des plats depuis un ou plusieurs restaurants." action={<Link className="btn-primary" to="/">Voir les restaurants</Link>} />;

  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
      <div className="space-y-4">
        <PageTitle title="Mon panier" subtitle={byRestaurant.length > 1 ? `${byRestaurant.length} restaurants : chaque repas voyage dans son propre compartiment.` : undefined} />
        {byRestaurant.map(([id, group]) => (
          <div key={id} className="card space-y-2">
            <h2>{group.name}</h2>
            {group.lines.map((l) => (
              <div key={l.dishId} className="flex items-center gap-3 border-t border-ink-100 pt-2 text-sm">
                <div className="min-w-0 flex-1"><p className="font-medium">{l.name}</p><p className="text-ink-500">{fcfa(l.price)}</p></div>
                <button className="btn-ghost btn-sm" onClick={() => setQuantity(l.dishId, l.quantity - 1)} aria-label="Retirer un"><Minus className="h-3.5 w-3.5" /></button>
                <span className="w-6 text-center font-bold">{l.quantity}</span>
                <button className="btn-ghost btn-sm" onClick={() => setQuantity(l.dishId, l.quantity + 1)} aria-label="Ajouter un"><Plus className="h-3.5 w-3.5" /></button>
                <span className="w-24 text-right font-semibold">{fcfa(l.price * l.quantity)}</span>
                <button className="text-red-600" onClick={() => setQuantity(l.dishId, 0)} aria-label={`Supprimer ${l.name}`}><Trash2 className="h-4 w-4" /></button>
              </div>
            ))}
          </div>
        ))}
      </div>

      <aside className="card h-fit space-y-4">
        <h2>Finaliser la commande</h2>
        {!user && <p className="text-sm text-ink-500">Connectez-vous pour commander. <Link className="font-semibold text-brand-600" to="/connexion">Connexion</Link></p>}
        {user && !isCustomer && <p className="text-sm text-ink-500">Seul un compte client peut passer commande.</p>}
        {isCustomer && (
          <>
            <div>
              <span className="label">Adresse de livraison</span>
              {addresses.data?.length === 0 ? (
                <p className="text-sm text-ink-500">Aucune adresse enregistrée. <Link className="font-semibold text-brand-600" to="/profil">Ajouter une adresse</Link></p>
              ) : (
                <select className="input" value={chosen ?? ''} onChange={(e) => setAddressId(Number(e.target.value))}>
                  {addresses.data?.map((a) => <option key={a.id} value={a.id}>{a.label} — {a.line}</option>)}
                </select>
              )}
            </div>
            <div>
              <span className="label">Paiement</span>
              <div className="space-y-1.5">
                {(config.data?.paymentMethods ?? []).map((m) => (
                  <label key={m} className={`flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 text-sm ${method === m ? 'border-brand-500 bg-brand-50' : 'border-ink-100'}`}>
                    <input type="radio" name="payment" checked={method === m} onChange={() => setMethod(m)} />
                    {paymentMethodLabel[m]}
                    {m !== 'CASH_ON_DELIVERY' && config.data?.paymentsSandbox && <span className="ml-auto rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-800">MODE TEST</span>}
                  </label>
                ))}
              </div>
            </div>
          </>
        )}
        <dl className="space-y-1 border-t border-ink-100 pt-3 text-sm">
          <div className="flex justify-between"><dt className="text-ink-500">Sous-total</dt><dd>{fcfa(subtotal)}</dd></div>
          <div className="flex justify-between"><dt className="text-ink-500">Livraison</dt><dd>{fcfa(fee)}</dd></div>
          <div className="flex justify-between text-base font-bold"><dt>Total</dt><dd>{fcfa(subtotal + fee)}</dd></div>
        </dl>
        <button className="btn-primary w-full" disabled={!isCustomer || !chosen || checkout.isPending} onClick={() => checkout.mutate()}>
          {checkout.isPending ? 'Envoi…' : 'Commander'}
        </button>
      </aside>
    </div>
  );
}
