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
          switch (event.type) {
            case 'notification': {
              const n = event.payload as Notification;
              toast.info(`${n.title} — ${n.body}`);
              queryClient.invalidateQueries({ queryKey: ['notifications'] });
              break;
            }
            case 'box.updated':
              queryClient.invalidateQueries({ queryKey: ['boxes'] });
              queryClient.invalidateQueries({ queryKey: ['hardware'] });
              queryClient.invalidateQueries({ queryKey: ['orders'] });
              queryClient.invalidateQueries({ queryKey: ['courier'] });
              break;
            case 'order.updated':
              queryClient.invalidateQueries({ queryKey: ['orders'] });
              queryClient.invalidateQueries({ queryKey: ['courier'] });
              queryClient.invalidateQueries({ queryKey: ['admin'] });
              break;
            case 'delivery.updated':
            case 'delivery.location':
              queryClient.invalidateQueries({ queryKey: ['tracking'] });
              queryClient.invalidateQueries({ queryKey: ['courier'] });
              queryClient.invalidateQueries({ queryKey: ['orders'] });
              break;
          }
        });
      },
    });
    client.activate();
    return () => { void client.deactivate(); };
  }, [token, queryClient]);
}
