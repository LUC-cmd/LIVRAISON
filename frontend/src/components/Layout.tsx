import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, LogOut, Menu, ShoppingBag, User as UserIcon, X } from 'lucide-react';
import { get, post } from '../api/client';
import { dateTime } from '../api/labels';
import type { Notification } from '../api/types';
import { useRealtime } from '../api/realtime';
import { cartCount, useCart } from '../stores/cart';
import { hasRole, homeFor, useSession } from '../stores/session';
import { Toasts } from './ui';

interface NavItem { to: string; label: string; end?: boolean }

function navFor(user: ReturnType<typeof useSession.getState>['user']): NavItem[] {
  if (hasRole(user, 'ADMIN', 'SUPERADMIN')) {
    const items: NavItem[] = [
      { to: '/admin', label: 'Tableau de bord', end: true }, { to: '/admin/commandes', label: 'Commandes' }, { to: '/admin/coffres', label: 'Coffres' },
      { to: '/admin/restaurants', label: 'Restaurants' }, { to: '/admin/utilisateurs', label: 'Utilisateurs' }, { to: '/admin/livraisons', label: 'Livraisons' },
      { to: '/admin/paiements', label: 'Paiements' }, { to: '/admin/evenements', label: 'Événements' }, { to: '/admin/reclamations', label: 'Réclamations' },
    ];
    if (hasRole(user, 'SUPERADMIN')) items.push({ to: '/admin/superadmin', label: 'Superadmin' });
    return items;
  }
  if (hasRole(user, 'RESTAURANT')) return [{ to: '/restaurant/commandes', label: 'Commandes' }, { to: '/restaurant/menu', label: 'Menu' }, { to: '/restaurant/statistiques', label: 'Ventes' }, { to: '/restaurant', label: 'Profil', end: true }];
  if (hasRole(user, 'COURIER')) return [{ to: '/livreur', label: 'Missions', end: true }, { to: '/livreur/historique', label: 'Historique et revenus' }];
  if (hasRole(user, 'CUSTOMER')) return [{ to: '/', label: 'Restaurants', end: true }, { to: '/commandes', label: 'Mes commandes' }, { to: '/reclamations', label: 'Réclamations' }];
  return [{ to: '/', label: 'Restaurants', end: true }];
}

function Notifications() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['notifications'], queryFn: () => get<{ unread: number; items: Notification[] }>('/notifications'), refetchInterval: 30000 });
  const readAll = useMutation({ mutationFn: () => post('/notifications/read-all'), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }) });
  const readOne = useMutation({ mutationFn: (id: number) => post(`/notifications/${id}/read`), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }) });
  const unread = query.data?.unread ?? 0;
  return (
    <div className="relative">
      <button className="relative rounded-full p-2 hover:bg-ink-50" onClick={() => setOpen((o) => !o)} aria-label={`Notifications (${unread} non lues)`}>
        <Bell className="h-5 w-5" />
        {unread > 0 && <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-500 px-1 text-[10px] font-bold text-white">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="rise absolute right-0 z-[1500] mt-2 w-[min(92vw,380px)] rounded-2xl border border-ink-100 bg-white shadow-soft">
          <div className="flex items-center justify-between border-b border-ink-100 px-4 py-2">
            <p className="font-semibold">Notifications</p>
            <button className="text-xs font-semibold text-brand-600 disabled:opacity-40" disabled={unread === 0 || readAll.isPending} onClick={() => readAll.mutate()}>Tout marquer comme lu</button>
          </div>
          <div className="max-h-96 overflow-y-auto">
            {(query.data?.items ?? []).length === 0 && <p className="px-4 py-6 text-center text-sm text-ink-500">Aucune notification.</p>}
            {(query.data?.items ?? []).map((n) => (
              <button key={n.id} className={`block w-full border-b border-ink-100 px-4 py-3 text-left text-sm hover:bg-ink-50 ${n.readAt ? 'opacity-60' : ''}`}
                onClick={() => { if (!n.readAt) readOne.mutate(n.id); if (n.link) { navigate(n.link); setOpen(false); } }}>
                <p className="font-semibold">{n.title}</p>
                <p className="text-ink-700">{n.body}</p>
                <p className="mt-1 text-xs text-ink-500">{dateTime(n.createdAt)}</p>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function Layout() {
  const { user, refreshToken, clear } = useSession();
  const lines = useCart((s) => s.lines);
  const [menu, setMenu] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  useRealtime();
  const nav = navFor(user);
  const showCart = !user || hasRole(user, 'CUSTOMER');

  const logout = async () => {
    try { if (refreshToken) await post('/auth/logout', { refreshToken }); } catch { /* la session locale est fermee de toute facon */ }
    clear();
    queryClient.clear();
    navigate('/');
  };
  const linkClass = ({ isActive }: { isActive: boolean }) => `rounded-lg px-3 py-1.5 text-sm font-medium ${isActive ? 'bg-ink-900 text-white' : 'text-ink-700 hover:bg-ink-100'}`;

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-[1200] border-b border-ink-100 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-2.5">
          <button className="rounded-lg p-2 hover:bg-ink-50 lg:hidden" onClick={() => setMenu((m) => !m)} aria-label="Menu">{menu ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}</button>
          <Link to={homeFor(user)} className="flex items-center gap-2 font-black tracking-tight">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-brand-500 text-white">S</span>
            <span>SmartFood<span className="text-brand-500">.</span></span>
          </Link>
          <nav className="ml-4 hidden flex-1 flex-wrap items-center gap-1 lg:flex">
            {nav.map((item) => <NavLink key={item.to} to={item.to} end={item.end} className={linkClass}>{item.label}</NavLink>)}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            {showCart && (
              <Link to="/panier" className="relative rounded-full p-2 hover:bg-ink-50" aria-label="Panier">
                <ShoppingBag className="h-5 w-5" />
                {cartCount(lines) > 0 && <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-ink-900 px-1 text-[10px] font-bold text-white">{cartCount(lines)}</span>}
              </Link>
            )}
            {user ? (
              <>
                <Notifications />
                <Link to="/profil" className="rounded-full p-2 hover:bg-ink-50" aria-label="Mon profil"><UserIcon className="h-5 w-5" /></Link>
                <button className="rounded-full p-2 hover:bg-ink-50" onClick={logout} aria-label="Se déconnecter"><LogOut className="h-5 w-5" /></button>
              </>
            ) : (
              <>
                <Link to="/connexion" className="btn-ghost btn-sm">Connexion</Link>
                <Link to="/inscription" className="btn-primary btn-sm hidden whitespace-nowrap sm:inline-flex">Créer un compte</Link>
              </>
            )}
          </div>
        </div>
        {menu && (
          <nav className="flex flex-col gap-1 border-t border-ink-100 px-4 py-2 lg:hidden">
            {nav.map((item) => <NavLink key={item.to} to={item.to} end={item.end} className={linkClass} onClick={() => setMenu(false)}>{item.label}</NavLink>)}
          </nav>
        )}
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6"><Outlet /></main>
      <Toasts />
    </div>
  );
}
