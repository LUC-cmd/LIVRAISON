import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthResponse, Role, User } from '../api/types';

interface SessionState {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  setSession: (auth: AuthResponse) => void;
  setUser: (user: User) => void;
  clear: () => void;
}

export const useSession = create<SessionState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setSession: (auth) => set({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user }),
      setUser: (user) => set({ user }),
      clear: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    { name: 'smartfood-session' },
  ),
);

export const hasRole = (user: User | null, ...roles: Role[]) => !!user && user.roles.some((r) => roles.includes(r));

/** Page d'accueil de chaque role apres connexion. */
export function homeFor(user: User | null): string {
  if (hasRole(user, 'ADMIN', 'SUPERADMIN')) return '/admin';
  if (hasRole(user, 'RESTAURANT')) return '/restaurant';
  if (hasRole(user, 'COURIER')) return '/livreur';
  return '/';
}
