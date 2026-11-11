# SmartFood Delivery

Plateforme de livraison de repas avec coffres électroniques à compartiments individuels.
Version 1 : prototype logiciel complet, coffre **simulé**, prêt pour le branchement d'un ESP32 en version 2.

- `backend/` — API Spring Boot 3 (Java 17+), PostgreSQL, Flyway, JWT, WebSocket STOMP, Swagger.
- `frontend/` — application web React + TypeScript + Vite + Tailwind (client, restaurant, livreur, administrateur).
- `docker-compose.yml` — PostgreSQL local.

## État de vérification — à lire avant de commencer

| Élément | Vérifié | Comment |
| --- | --- | --- |
| Migration SQL `V1__schema.sql` | Oui | Exécutée sur PostgreSQL 16 (36 tables). |
| Correspondance entités JPA ↔ tables, noms des méthodes de dépôts | Oui | Contrôle automatique contre le schéma réel. |
| Frontend | Oui, partiellement | `npm run build` (TypeScript strict) passe ; deux écrans affichés avec des réponses d'API factices. |
| Backend Java | **Non compilé avec Maven** | Maven Central était inaccessible dans l'environnement de développement. Le code a seulement été contrôlé par `javac` contre des signatures simplifiées de Spring. |
| Tests des 12 scénarios (`DeliveryScenariosTest`) | **Écrits, jamais exécutés** | À lancer chez vous : `mvn test`. |
| Parcours complet frontend ↔ backend | **Non testé** | Dépend du point précédent. |

La première chose à faire est donc `mvn test` dans `backend/`. Si une erreur de compilation ou un test échoue, transmettez le message : la correction est en général immédiate.

## Prérequis

- JDK 17 ou plus récent, Maven 3.9+
- Node.js 20+
- PostgreSQL 16 (ou Docker Desktop pour `docker compose`)

## Lancement local

```bash
# 1. Base de données (crée smartfood et smartfood_test)
docker compose up -d

# 2. Backend : http://localhost:8080  —  Swagger : http://localhost:8080/swagger-ui.html
cd backend
mvn test              # scénarios 1 à 12 sur la base smartfood_test
mvn spring-boot:run

# 3. Frontend : http://localhost:5173
cd ../frontend
npm install
npm run dev
```

Sans Docker : créez un rôle `smartfood` (mot de passe `smartfood`) et deux bases `smartfood` et `smartfood_test` qui lui appartiennent.

Sous Windows PowerShell, les commandes sont identiques.

## Comptes de démonstration

Créés au premier démarrage (`DEMO_DATA=true`). Mot de passe commun : `Demo1234!`

| Rôle | Email |
| --- | --- |
| Clients | `kossi@demo.tg`, `ama@demo.tg` |
| Restaurants | `afi@demo.tg` (Chez Afi), `yao@demo.tg` (Le Fufu d'Or), `edem@demo.tg` (Grill du Boulevard) |
| Livreur | `koffi@demo.tg` — transporte « Coffre Moto 01 » (6 compartiments) |
| Administrateur | `admin@demo.tg` |
| Superadministrateur | `superadmin@smartfood.tg` / `ChangeMe!2026` (variables `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`) |

Ouvrez un navigateur (ou une fenêtre privée) par rôle pour suivre un parcours en temps réel.

## Jouer les scénarios dans l'interface

1. **Client** : choisir des plats, panier, commander (paiement à la livraison, ou Mixx/Flooz/carte en mode test).
2. **Restaurant** › Commandes : Accepter → Repas prêt (un compartiment est réservé) → Ouvrir le compartiment → sur le coffre virtuel : sélectionner le compartiment, Déposer le repas, Fermer la porte. Le serveur vérifie le verrouillage puis génère le code.
3. **Client** › commande : le code de retrait apparaît (bouton Afficher). Lui seul peut le voir.
4. **Livreur** : Accepter → Prise en charge → Départ → activer le GPS réel ou le mode simulation (toucher la carte) → Arrivé → Espèces reçues si paiement à la livraison.
5. **Client** : suit le livreur sur la carte, puis saisit son code sur le clavier du coffre ; seul son compartiment s'ouvre. Retirer le repas, fermer la porte : le retrait est enregistré, le code invalidé, le compartiment libéré.
6. **Administrateur** › Coffres : pannes simulables — couper la connexion, batterie faible, perdre la prochaine commande d'ouverture, porte mal fermée, serrure bloquée ou défectueuse, capteur défectueux (puis « Lancer la surveillance des capteurs »), ouverture forcée. Codes incorrects répétés : le clavier se bloque. Tout est tracé dans Événements.

Code expiré : dans la base, `update withdrawal_codes set expires_at = now() - interval '1 minute' where status = 'ACTIVE';` puis saisir le code ; le client peut ensuite demander un nouveau code.

## Configuration (variables d'environnement du backend)

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | base locale `smartfood` | Connexion PostgreSQL |
| `JWT_SECRET` | valeur de développement | **À changer** : 32 caractères minimum |
| `CODE_HMAC_SECRET`, `CODE_AES_KEY` | valeurs de développement | **À changer** : secret d'empreinte des codes ; clé AES-256 en base64 (`openssl rand -base64 32`) |
| `CODE_VALIDITY_MINUTES` | 120 | Durée de validité d'un code de retrait |
| `DELIVERY_FEE`, `COMMISSION_PERCENT`, `COURIER_SHARE_PERCENT` | 500, 15, 80 | Modèle économique (FCFA et %) |
| `SIMULATOR_ENABLED` | true | Coffre virtuel et positions GPS simulées |
| `PAYMENTS_SANDBOX` | true | Prestataire de paiement de test |
| `DEMO_DATA` | true | Jeu de données de démonstration |
| `CORS_ORIGINS` | `http://localhost:5173` | Origines autorisées (séparées par des virgules) |
| `STORAGE_DIR` | `./storage` | Dossier des images (hors base de données) |
| `SPRING_MAIL_HOST` (+ port, identifiants) | absent | Active l'envoi d'emails |

Production : `SPRING_PROFILES_ACTIVE=prod` désactive le simulateur, les paiements de test et les données de démonstration.

## Ce qui est réel, ce qui est simulé, ce qui reste à brancher

- **Réel** : comptes et rôles, catalogue, commandes multi-restaurants (une sous-commande et un compartiment par restaurant), réservation transactionnelle des compartiments, autorisations de chargement, codes (empreinte HMAC-SHA256, copie chiffrée AES-GCM pour le réaffichage au client), journalisation, notifications dans l'application et en temps réel, GPS réel du téléphone du livreur.
- **Simulé** (identifié comme tel à l'écran) : le matériel du coffre (`simulator/`), les positions GPS en mode test, les paiements Mixx/Flooz/carte en mode test.
- **À brancher** :
  - Paiements réels : implémenter `PaymentProvider` avec l'API officielle de chaque prestataire (clés par variables d'environnement, webhook sur `/api/v1/payments/webhooks/{prestataire}`).
  - SMS, WhatsApp, push : ajouter des canaux dans `NotificationService`, sur le modèle de l'email.
  - Matériel : implémenter `DeviceGateway` en MQTT sur TLS pour envoyer les commandes à l'ESP32. La remontée d'événements d'un appareil réel existe déjà en HTTPS : `POST /api/v1/device/events` avec les en-têtes `X-Device-Uid` et `X-Device-Secret`.
  - Stockage d'images en production : implémenter `FileStorage` vers un stockage objet (S3, MinIO).

## Points d'attention

- **GPS sur téléphone** : les navigateurs n'autorisent la géolocalisation qu'en HTTPS (ou sur `localhost`). Pour tester sur un téléphone Android, servez l'application en HTTPS ou utilisez le mode simulation.
- **Paiement à la livraison** : le code du client n'ouvre le compartiment qu'après « Espèces reçues » côté livreur.
- **Coffre hors connexion** : rien ne s'ouvre ; tout reste verrouillé jusqu'à la reconnexion.
- **Règle d'affectation** : un coffre n'accepte de nouvelles réservations que si son livreur est disponible et n'est pas déjà en route.
- Le dossier `storage/` et les secrets ne doivent pas être versionnés.

## Organisation du code

```
backend/src/main/java/tg/smartfood/
  common/  auth/  user/  restaurant/  order/  payment/  delivery/
  box/         réservation, autorisations, codes, réaction aux événements du coffre
  iot/         DeviceGateway (commandes) + DeviceEvents (événements) + entrée HTTPS des appareils réels
  simulator/   coffre virtuel : implémentation simulée de DeviceGateway, actions physiques, pannes
  notification/  realtime/  audit/  admin/  customer/  courier/  files/  bootstrap/
backend/src/main/resources/db/migration/V1__schema.sql
backend/src/test/java/tg/smartfood/DeliveryScenariosTest.java
frontend/src/
  api/  stores/  components/ (BoxSimulator, MapView, Layout)  pages/ (client, restaurant, courier, admin)
```
