import { useEffect } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { useSession } from '../stores/session';
import { toast } from '../stores/toast';
import type { Notification } from './types';

interface ServerEvent { type: string; payload: unknown }

/**
 * Connexion WebSocket STOMP authentifiee. Chaque evenement du serveur invalide les requetes concernees :
 * l'ecran se met a jour sans rechargement. La reconnexion est automatique.
 */
export function useRealtime() {
  const token = useSession((s) => s.accessToken);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!token) return;
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 4000,
      onConnect: () => {
        client.subscribe('/user/queue/events', (frame) => {
          const event = JSON.parse(frame.body) as ServerEvent;
          if (event.type === 'notification') {
            const n = event.payload as Notification;
            toast.info(`${n.title} — ${n.body}`);
          }
          // Tout evenement du serveur rafraichit les ecrans ouverts : commandes, coffres, missions, tableaux de bord.
          for (const key of ['notifications', 'orders', 'boxes', 'hardware', 'courier', 'tracking', 'admin', 'restaurant', 'restaurants', 'complaints']) {
            queryClient.invalidateQueries({ queryKey: [key] });
          }
        });
      },
    });
    client.activate();
    return () => { void client.deactivate(); };
  }, [token, queryClient]);
}
