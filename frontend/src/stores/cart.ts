import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Dish } from '../api/types';

export interface CartLine { dishId: number; name: string; price: number; quantity: number; restaurantId: number; restaurantName: string }

interface CartState {
  lines: CartLine[];
  add: (dish: Dish, restaurantName: string) => void;
  /** Remet des lignes dans le panier (commander a nouveau). Les quantites s'ajoutent a celles deja presentes. */
  addLines: (lines: CartLine[]) => void;
  setQuantity: (dishId: number, quantity: number) => void;
  clear: () => void;
}

/** Panier multi-restaurants. Les prix affiches sont indicatifs : le serveur recalcule tout a la commande. */
export const useCart = create<CartState>()(
  persist(
    (set) => ({
      lines: [],
      add: (dish, restaurantName) =>
        set((s) => {
          const found = s.lines.find((l) => l.dishId === dish.id);
          if (found) return { lines: s.lines.map((l) => (l.dishId === dish.id ? { ...l, quantity: Math.min(50, l.quantity + 1) } : l)) };
          return { lines: [...s.lines, { dishId: dish.id, name: dish.name, price: dish.price, quantity: 1, restaurantId: dish.restaurantId, restaurantName }] };
        }),
      addLines: (lines) =>
        set((s) => {
          const next = [...s.lines];
          for (const line of lines) {
            const i = next.findIndex((l) => l.dishId === line.dishId);
            if (i >= 0) next[i] = { ...next[i], quantity: Math.min(50, next[i].quantity + line.quantity) };
            else next.push(line);
          }
          return { lines: next };
        }),
      setQuantity: (dishId, quantity) =>
        set((s) => ({ lines: quantity <= 0 ? s.lines.filter((l) => l.dishId !== dishId) : s.lines.map((l) => (l.dishId === dishId ? { ...l, quantity: Math.min(50, quantity) } : l)) })),
      clear: () => set({ lines: [] }),
    }),
    { name: 'smartfood-cart' },
  ),
);

export const cartCount = (lines: CartLine[]) => lines.reduce((n, l) => n + l.quantity, 0);
export const cartSubtotal = (lines: CartLine[]) => lines.reduce((n, l) => n + l.quantity * l.price, 0);
