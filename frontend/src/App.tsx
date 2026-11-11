import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import type { Role } from './api/types';
import { Layout } from './components/Layout';
import { Empty } from './components/ui';
import { LoginPage, RegisterPage } from './pages/Auth';
import { ProfilePage } from './pages/Profile';
import { CreditsPage } from './pages/Credits';
import { SimulationPage } from './pages/Simulation';
import { CartPage } from './pages/client/Cart';
import { HomePage } from './pages/client/Home';
import { ComplaintsPage, OrderDetailPage, OrdersPage } from './pages/client/Orders';
import { RestaurantPage } from './pages/client/Restaurant';
import { CourierHistoryPage, CourierPage } from './pages/courier/Courier';
import { RestaurantMenuPage, RestaurantOrdersPage, RestaurantProfilePage, RestaurantStatsPage } from './pages/restaurant/Restaurant';
import { AdminBoxesPage, AdminComplaintsPage, AdminDashboardPage, AdminDeliveriesPage, AdminEventsPage, AdminOrdersPage, AdminPaymentsPage, AdminRestaurantsPage, AdminUsersPage, SuperAdminPage } from './pages/admin/Admin';
import { hasRole, homeFor, useSession } from './stores/session';

/** Garde d'affichage. La vraie protection est cote serveur : chaque API verifie le role et la propriete des donnees. */
function Guard({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const user = useSession((s) => s.user);
  if (!user) return <Navigate to="/connexion" replace />;
  if (roles && !hasRole(user, ...roles)) return <Navigate to={homeFor(user)} replace />;
  return <>{children}</>;
}

/** L'accueil public est le catalogue ; les autres roles sont envoyes vers leur espace. */
function Landing() {
  const user = useSession((s) => s.user);
  if (user && !hasRole(user, 'CUSTOMER')) return <Navigate to={homeFor(user)} replace />;
  return <HomePage />;
}

export default function App() {
  const customer: Role[] = ['CUSTOMER'];
  const restaurant: Role[] = ['RESTAURANT'];
  const courier: Role[] = ['COURIER'];
  const admin: Role[] = ['ADMIN', 'SUPERADMIN'];
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Landing />} />
        <Route path="/restaurants/:id" element={<RestaurantPage />} />
        <Route path="/panier" element={<CartPage />} />
        <Route path="/connexion" element={<LoginPage />} />
        <Route path="/inscription" element={<RegisterPage />} />
        <Route path="/credits" element={<CreditsPage />} />
        <Route path="/simulation" element={<SimulationPage />} />
        <Route path="/profil" element={<Guard><ProfilePage /></Guard>} />

        <Route path="/commandes" element={<Guard roles={customer}><OrdersPage /></Guard>} />
        <Route path="/commandes/:id" element={<Guard roles={customer}><OrderDetailPage /></Guard>} />
        <Route path="/reclamations" element={<Guard roles={customer}><ComplaintsPage /></Guard>} />

        <Route path="/restaurant" element={<Guard roles={restaurant}><RestaurantProfilePage /></Guard>} />
        <Route path="/restaurant/commandes" element={<Guard roles={restaurant}><RestaurantOrdersPage /></Guard>} />
        <Route path="/restaurant/menu" element={<Guard roles={restaurant}><RestaurantMenuPage /></Guard>} />
        <Route path="/restaurant/statistiques" element={<Guard roles={restaurant}><RestaurantStatsPage /></Guard>} />

        <Route path="/livreur" element={<Guard roles={courier}><CourierPage /></Guard>} />
        <Route path="/livreur/historique" element={<Guard roles={courier}><CourierHistoryPage /></Guard>} />

        <Route path="/admin" element={<Guard roles={admin}><AdminDashboardPage /></Guard>} />
        <Route path="/admin/commandes" element={<Guard roles={admin}><AdminOrdersPage /></Guard>} />
        <Route path="/admin/coffres" element={<Guard roles={admin}><AdminBoxesPage /></Guard>} />
        <Route path="/admin/restaurants" element={<Guard roles={admin}><AdminRestaurantsPage /></Guard>} />
        <Route path="/admin/utilisateurs" element={<Guard roles={admin}><AdminUsersPage /></Guard>} />
        <Route path="/admin/livraisons" element={<Guard roles={admin}><AdminDeliveriesPage /></Guard>} />
        <Route path="/admin/paiements" element={<Guard roles={admin}><AdminPaymentsPage /></Guard>} />
        <Route path="/admin/evenements" element={<Guard roles={admin}><AdminEventsPage /></Guard>} />
        <Route path="/admin/reclamations" element={<Guard roles={admin}><AdminComplaintsPage /></Guard>} />
        <Route path="/admin/superadmin" element={<Guard roles={['SUPERADMIN']}><SuperAdminPage /></Guard>} />

        <Route path="*" element={<Empty title="Page introuvable" hint="Cette adresse ne correspond à aucune page." />} />
      </Route>
    </Routes>
  );
}
