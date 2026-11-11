import type { CompartmentState, DeliveryStatus, HwFault, OrderStatus, PaymentMethod, PaymentStatus, Role, SizeClass } from './types';

export const fcfa = (n: number) => `${n.toLocaleString('fr-FR')} FCFA`;
export const dateTime = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—');
export const timeOnly = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) : '—');

export const orderStatusLabel: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: 'En attente de paiement', PENDING: 'Envoyée au restaurant', ACCEPTED: 'En préparation', READY: 'Prête, attend un compartiment',
  RESERVED: 'Compartiment réservé', LOADED: 'Verrouillée dans le coffre', IN_TRANSIT: 'En route', ARRIVED: 'Livreur arrivé',
  COLLECTED: 'Retirée', REJECTED: 'Refusée', CANCELLED: 'Annulée',
};
export const compartmentStateLabel: Record<CompartmentState, string> = {
  AVAILABLE: 'Disponible', RESERVED: 'Réservé', LOADING: 'En chargement', OCCUPIED: 'Occupé', LOCKED: 'Verrouillé', WITHDRAWING: 'En retrait', OPEN: 'Ouvert', FAULT: 'En panne',
};
export const deliveryStatusLabel: Record<DeliveryStatus, string> = {
  OFFERED: 'Proposée', ACCEPTED: 'Acceptée', PICKED_UP: 'Coffre pris en charge', IN_TRANSIT: 'En route', ARRIVED: 'Arrivé', COMPLETED: 'Terminée', CANCELLED: 'Annulée',
};
export const paymentMethodLabel: Record<PaymentMethod, string> = { CASH_ON_DELIVERY: 'Paiement à la livraison', MIXX: 'Mixx by Yas', FLOOZ: 'Flooz', CARD: 'Carte bancaire' };
export const paymentStatusLabel: Record<PaymentStatus, string> = { PENDING: 'En attente', SUCCEEDED: 'Réussi', FAILED: 'Échoué', REFUNDED: 'Remboursé' };
export const sizeLabel: Record<SizeClass, string> = { SMALL: 'Petit', MEDIUM: 'Moyen', LARGE: 'Grand' };
export const roleLabel: Record<Role, string> = { CUSTOMER: 'Client', RESTAURANT: 'Restaurant', COURIER: 'Livreur', ADMIN: 'Administrateur', SUPERADMIN: 'Superadministrateur' };
export const faultLabel: Record<HwFault, string> = { NONE: 'Aucune panne', DOOR_AJAR: 'Porte mal fermée', LOCK_STUCK: 'Serrure bloquée (ne s’ouvre pas)', LOCK_BROKEN: 'Serrure défectueuse (ne se verrouille pas)', SENSOR_BROKEN: 'Capteur défectueux' };
export const dayLabel = ['', 'Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];
