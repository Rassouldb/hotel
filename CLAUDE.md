# PMS Niirma — consignes pour Claude

## Référence
- Cahier des charges : `docs/specs/cahier_des_charges_v2.md` (source de vérité ; les sections sont citées `§N`).
- Le développement suit les itérations du §45.1 ; chaque itération : cadrage des user stories → code + tests → vérification → revue.
- Langue : code, identifiants et noms de tables en anglais ; commentaires, messages d'erreur, textes d'interface et documentation en français.

## Commandes
- Dépendances locales : `docker compose up -d postgres mailpit`
- Backend : `cd backend && ./mvnw spring-boot:run` · tests : `./mvnw verify`
- Frontend : `cd frontend && npm run dev` · contrôles : `npm run lint && npm test && npm run build`
- Sur ce poste, JAVA_HOME pointe vers un JDK 17 : préfixer les commandes Maven par `JAVA_HOME="/c/Program Files/Java/jdk-22"`.
- Sur ce poste, le réseau coupe TLS 1.3 vers le registre npm : préfixer les commandes npm par `NODE_OPTIONS=--tls-max-v1.2` (et `PMS_BUILD_NODE_OPTIONS=--tls-max-v1.2` pour `docker compose build`).
- Docker n'est pas toujours dans le PATH des shells ouverts avant son installation : `C:\Program Files\Docker\Docker\resources\bin`.

## Backend (`backend/`, package racine `sn.niirma`)
- Chaque sous-package direct de `sn.niirma` est un module Spring Modulith ; `ModularityTests` vérifie les frontières. Les types publics d'un module sont dans son package racine ; le reste va dans `internal`.
- `shared` est le noyau partagé : `ApiResponse`/`PageMeta` (enveloppe), `BusinessException` (erreurs métier avec code stable), `TraceIdFilter`, `BaseEntity` (UUID v7, horodatages, `@Version`).
- Accès (§7) : chaque méthode de contrôleur porte exactement une annotation `@RequiresPermission("CODE")`, `@AuthenticatedOnly` ou `@PublicEndpoint` (vérifié par `AccessRulesIntegrationTest`). Une URL contenant `{hotelId}` est contrôlée pour cet hôtel ; un hôtel non affecté répond 404. Utilisateur courant : `CurrentUser.require()` ; droits : `AccessControl` (relus en base à chaque requête).
- Nouvelles permissions : les ajouter à l'énumération `iam.Permission` (synchronisée au démarrage) et, si besoin, aux rôles par défaut (`DefaultRoles`).
- Erreurs : lever `BusinessException` avec un code en MAJUSCULES (§34.4) ; ne jamais exposer de détail technique.
- Opérations critiques (§32.4) : annoter la méthode du contrôleur avec `@Idempotent` et renvoyer un `ResponseEntity`.
- Audit (§32.1) : `AuditService.record` dans la transaction métier, `recordFailure` pour un échec. La table `audit_log` refuse UPDATE/DELETE.
- Schéma : uniquement par migrations Flyway (`db/migration`), Hibernate en `validate`. Données de démo dans `db/seed` (profils dev et test). Pas de `char(n)` : `varchar(n)`.
- Montants : `BigDecimal` + `NUMERIC(18,2)`, jamais `double`. Dates métier : `LocalDate` dans le fuseau de l'hôtel ; horodatages : `OffsetDateTime` UTC.
- Jackson 3 (`tools.jackson.*`) ; les annotations restent `com.fasterxml.jackson.annotation`. Un booléen absent du JSON vaut `false` (`fail-on-null-for-primitives: false`).
- Tests d'intégration : annoter avec `@IntegrationTest` (Testcontainers PostgreSQL, MockMvcTester), suffixe `IntegrationTest`. Se connecter avec `TestAuth` (`admin()`, `demo("reception")`, `createActiveUser(...)`) ; `MutableClock` simule l'écoulement du temps, `CapturingMailSender` capture les emails.

## Frontend (`frontend/`)
- Appels API via `apiRequest` (`src/api/http.ts`) : déballe l'enveloppe, lève `ApiError` (code, message, traceId), joint le jeton d'accès (gardé en mémoire uniquement) et renouvelle la session une fois sur un 401. Passer `idempotencyKey` pour les opérations critiques, `publicEndpoint: true` pour les appels hors session.
- Session et droits : `useAuth()` (`profile`, `can(permission, hotelId?)`, `logout`). Les écrans protégés sont sous `RequireAuth` ; une entrée de menu déclare ses permissions (`anyOf`) dans `navigation.tsx`.
- Formulaires : React Hook Form + Zod ; erreurs serveur par champ via `fieldErrors(error)`.
- Tests : `src/test/fakeApi.ts` (faux serveur) et `src/test/renderApp.tsx` (application complète avec ses routes).
- Textes dans `src/i18n/fr.ts`, jamais en dur dans les composants.
- Menu : `src/layout/navigation.tsx` ; un module non livré affiche la page « en préparation ».
- Formats locaux : `src/app/format.ts` (dates JJ/MM/AAAA, montants « 146 000 XOF »).
