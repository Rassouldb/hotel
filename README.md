# PMS Niirma — Système de gestion hôtelière

Property Management System couvrant le cycle complet d'un séjour : réservation, check-in, séjour, clôture journalière, check-out, facturation, paiements, caisse et reporting. Conçu pour le marché d'Afrique de l'Ouest (XOF, Mobile Money, fiche de police).

Le périmètre, les règles métier et l'architecture sont décrits dans le [cahier des charges v2](docs/specs/cahier_des_charges_v2.md).

## Stack

| Couche | Technologies |
| --- | --- |
| Frontend | React 19, TypeScript, Vite, MUI, TanStack Query, React Router, i18next |
| Backend | Java 21, Spring Boot 4.1, Spring Data JPA, Flyway, Spring Modulith |
| Base de données | PostgreSQL 17 |
| Outillage | Docker Compose, Testcontainers, Vitest, GitHub Actions |

## Démarrage rapide

Prérequis : Docker Desktop, JDK 21 ou plus récent, Node.js 22.

```bash
docker compose up --build
```

Application : <http://localhost:8081> · emails capturés : <http://localhost:8025>.

### Comptes de développement

Créés automatiquement au démarrage avec le profil `dev` (jamais en production) :

| Identifiant | Mot de passe | Profil |
| --- | --- | --- |
| `admin` | `Admin#Dev2026` | Super administrateur |
| `direction` | `Demo#Pms2026` | Directeur — Hôtel Démo Dakar |
| `reception` | `Demo#Pms2026` | Réceptionniste — Hôtel Démo Dakar |
| `caisse` | `Demo#Pms2026` | Caissier — Hôtel Démo Dakar |
| `comptable` | `Demo#Pms2026` | Comptable — Dakar et Saly |
| `menage` | `Demo#Pms2026` | Agent housekeeping — Hôtel Démo Dakar |

### Production

Variables obligatoires : `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `PMS_JWT_SECRET` (256 bits minimum en Base64, ex. `openssl rand -base64 48`), `PMS_FRONTEND_URL`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`. Premier démarrage : `PMS_BOOTSTRAP_ADMIN_USERNAME` et `PMS_BOOTSTRAP_ADMIN_PASSWORD` créent le super administrateur (mot de passe à changer à la première connexion).

## Développement

Lancer les dépendances (PostgreSQL, Mailpit) :

```bash
docker compose up -d postgres mailpit
```

Backend (profil `dev`, données de démonstration incluses) — API sur <http://localhost:8080>, documentation sur <http://localhost:8080/swagger-ui.html> :

```bash
cd backend
./mvnw spring-boot:run
```

Frontend — <http://localhost:5173>, l'API est relayée par le proxy Vite :

```bash
cd frontend
npm install
npm run dev
```

## Tests

```bash
cd backend && ./mvnw verify
```

```bash
cd frontend && npm run lint && npm test
```

Les tests d'intégration du backend démarrent un PostgreSQL réel avec Testcontainers : Docker doit être lancé.

## Organisation du dépôt

```
backend/     API Spring Boot (un package par module métier sous sn.niirma)
frontend/    Application React
docs/specs/  Cahier des charges
docs/adr/    Décisions d'architecture
```
