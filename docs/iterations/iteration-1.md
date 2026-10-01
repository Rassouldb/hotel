# Itération 1 — Authentification, utilisateurs, rôles, permissions

**Statut** : validé le 2026-10-01 — en cours de réalisation
**Références** : cahier des charges §6, §7, §32.1, §34.5, §39

## Objectif

À la fin de l'itération, seule une personne authentifiée accède au PMS. Elle ne voit et ne fait que ce que ses rôles lui permettent, et seulement dans les hôtels qui lui sont affectés. Un administrateur gère les comptes, les rôles et les affectations, et toutes ces opérations sont auditées.

## Périmètre

| Inclus | Reporté |
| --- | --- |
| Connexion, déconnexion, rafraîchissement de session | Double authentification (MVP 2) |
| Verrouillage après échecs, journal des connexions | Connexion via un fournisseur externe (SSO) |
| Mot de passe : changement, oubli, réinitialisation | |
| Sessions actives : consultation, révocation | |
| Profil utilisateur | |
| Utilisateurs : création, modification, désactivation | |
| Rôles personnalisés, catalogue des permissions | |
| Affectation des rôles par hôtel, choix de l'hôtel actif | |
| Contrôle d'accès de chaque endpoint (permission + hôtel) | |
| Consultation du journal d'audit | |
| Rôles par défaut (§7.5) et super administrateur initial | |

## Décisions de conception

| Sujet | Décision |
| --- | --- |
| Jeton d'accès | JWT signé (HS256, secret de 256 bits fourni par l'environnement), valable 15 minutes, conservé **en mémoire** par le frontend (jamais dans localStorage) |
| Jeton de rafraîchissement | Valeur aléatoire opaque de 256 bits, valable 7 jours, stockée **hachée** (SHA-256), envoyée en cookie `httpOnly`, `Secure`, `SameSite=Strict`, limité au chemin `/api/v1/auth` ; nouveau jeton à chaque usage |
| Réutilisation d'un jeton déjà utilisé | Considérée comme un vol : toute la famille de sessions est révoquée |
| Inactivité | Une session sans requête pendant 30 minutes ne peut plus être rafraîchie |
| Mots de passe | Argon2id ; 10 caractères minimum ; refus des 10 000 mots de passe les plus courants |
| Permissions | Chargées côté serveur pour l'hôtel ciblé par la requête (pas dans le JWT), avec cache invalidé à chaque changement de rôle |
| Hôtel ciblé | Déduit de l'URL `/api/v1/hotels/{hotelId}/…` ; toute requête vers un hôtel non affecté reçoit **404** (on ne révèle pas son existence) |
| Super administrateur initial | Créé au premier démarrage à partir de `PMS_ADMIN_EMAIL` et `PMS_ADMIN_PASSWORD` ; en profil dev : `admin@demo.niirma.sn`, mot de passe documenté dans le README |
| Rôles système | Les 10 rôles par défaut du §7.5 peuvent voir leurs permissions modifiées mais ne peuvent pas être supprimés |
| Garde-fou | Impossible de désactiver ou de rétrograder le dernier super administrateur actif |

## User stories

### US-AUTH-01 — Se connecter

En tant qu'employé, je veux me connecter avec mon identifiant et mon mot de passe pour accéder au PMS.

- Nom d'utilisateur (insensible à la casse) et mot de passe valides → 200, jeton d'accès dans la réponse, cookie de rafraîchissement posé, connexion journalisée (`LOGIN_SUCCESS`).
- Identifiant inconnu ou mot de passe faux → 401 `INVALID_CREDENTIALS`, même message et même temps de réponse dans les deux cas.
- Compte désactivé → 401 `INVALID_CREDENTIALS` (on ne révèle pas l'état du compte).
- 5 échecs consécutifs → compte bloqué 15 minutes : 423 `ACCOUNT_LOCKED` avec l'heure de déblocage ; un succès remet le compteur à zéro.
- Plus de 10 tentatives par minute depuis la même adresse IP → 429 `TOO_MANY_REQUESTS`.

### US-AUTH-02 — Rester connecté

En tant qu'employé, je veux que ma session se prolonge pendant que je travaille, sans ressaisir mon mot de passe.

- Jeton d'accès expiré → le frontend appelle `POST /auth/refresh` et rejoue la requête, sans que l'utilisateur ne voie rien.
- Rafraîchissement réussi → nouveau jeton d'accès et nouveau cookie ; l'ancien cookie devient inutilisable.
- Réutilisation d'un ancien cookie → 401 `SESSION_REVOKED` et révocation de toutes les sessions issues de la même connexion.
- 30 minutes sans activité, ou plus de 7 jours → 401 `SESSION_EXPIRED`, retour à l'écran de connexion avec un message.

### US-AUTH-03 — Se déconnecter

- `POST /auth/logout` → session révoquée, cookie effacé, journalisation `LOGOUT` ; retour à l'écran de connexion.

### US-AUTH-04 — Changer mon mot de passe

- Ancien mot de passe correct et nouveau mot de passe conforme → mot de passe changé, **toutes mes autres sessions révoquées**, audit `PASSWORD_CHANGE`.
- Ancien mot de passe faux → 422 `INVALID_PASSWORD`.
- Nouveau mot de passe trop court ou trop courant → 422 `WEAK_PASSWORD` avec la règle non respectée.

### US-AUTH-05 — Mot de passe oublié

- `POST /auth/password/forgot` avec un email → **toujours** 202 avec le même message, que le compte existe ou non.
- Si le compte existe et est actif : email avec un lien à usage unique valable 30 minutes.
- Lien valide + nouveau mot de passe conforme → mot de passe changé, toutes les sessions révoquées, lien inutilisable.
- Lien expiré ou déjà utilisé → 422 `RESET_TOKEN_INVALID`.

### US-AUTH-06 — Mes sessions

- `GET /me/sessions` → liste de mes sessions actives (appareil / navigateur, IP, date de création, dernière activité, session courante signalée).
- `DELETE /me/sessions/{id}` → session révoquée ; impossible de révoquer la session d'un autre utilisateur (404).

### US-AUTH-07 — Mon profil

- `GET /me` → identité, langue, hôtels accessibles et, pour chacun, rôles et permissions effectives.
- `PUT /me` → modification du nom, du téléphone et de la langue (pas de l'email ni des rôles).

### US-IAM-01 — Gérer les utilisateurs

En tant qu'administrateur (`USER_*`), je veux gérer les comptes du personnel.

- Création : nom, prénom, nom d'utilisateur, email facultatif, téléphone, langue, et au moins une affectation (hôtel + rôle) sauf pour un super administrateur. La réponse contient le mot de passe temporaire, affiché une seule fois.
- Réinitialisation par l'administrateur (`USER_UPDATE`) : nouveau mot de passe temporaire, toutes les sessions de l'utilisateur révoquées.
- Nom d'utilisateur déjà utilisé → 409 `USERNAME_ALREADY_EXISTS`.
- Modification des informations et des affectations ; désactivation (`USER_DISABLE`) qui révoque immédiatement toutes ses sessions.
- Un directeur (DIR) ne gère que les utilisateurs des hôtels où il est affecté et ne peut pas attribuer une permission qu'il n'a pas lui-même.
- Le dernier super administrateur actif ne peut être ni désactivé ni rétrogradé → 409 `LAST_SUPER_ADMIN`.
- Chaque opération est auditée (sans jamais journaliser de mot de passe).

### US-IAM-02 — Gérer les rôles

- `ROLE_MANAGE` : créer un rôle, modifier son nom et ses permissions, supprimer un rôle personnalisé non affecté.
- Suppression d'un rôle système → 409 `SYSTEM_ROLE`. Suppression d'un rôle encore affecté → 409 `ROLE_IN_USE`.
- Une modification de permissions prend effet à la requête suivante des utilisateurs concernés.

### US-IAM-03 — Contrôle d'accès

- Requête sans jeton ou avec un jeton invalide ou expiré → 401 `UNAUTHENTICATED`.
- Permission absente pour l'hôtel ciblé → 403 `PERMISSION_DENIED`.
- Hôtel non affecté → 404 `HOTEL_NOT_FOUND`.
- `GET /hotels` ne renvoie que les hôtels affectés (tous pour le super administrateur).
- Un test automatisé vérifie que **chaque endpoint** déclare une règle d'accès : un endpoint ajouté sans règle fait échouer la build.

### US-IAM-04 — Choisir l'hôtel actif

- L'utilisateur affecté à plusieurs hôtels choisit l'hôtel actif dans le bandeau ; le menu et les actions s'adaptent à ses permissions dans cet hôtel.
- Un utilisateur affecté à un seul hôtel n'a pas de choix à faire.

### US-AUD-01 — Consulter le journal d'audit

- `AUDIT_VIEW` : liste paginée, filtrable par période, utilisateur, action, type et identifiant d'objet, résultat.
- Le journal indique désormais l'utilisateur auteur de chaque opération.

## Écrans

| Écran | Contenu |
| --- | --- |
| Connexion | Identifiant, mot de passe, lien « mot de passe oublié », messages de blocage |
| Mot de passe oublié / réinitialisation | Saisie de l'email ; saisie du nouveau mot de passe depuis le lien |
| Mon profil | Informations, changement de mot de passe, sessions actives |
| Utilisateurs | Liste filtrable, création, modification, affectations, désactivation |
| Rôles | Liste, matrice des permissions par module, création et modification |
| Journal d'audit | Liste filtrable, détail avant / après |
| Mise en page | Menu filtré par permissions, choix de l'hôtel, menu utilisateur (profil, déconnexion) |

## Données

Nouvelles tables (migration V4) : `app_user`, `role`, `permission`, `role_permission`, `user_hotel_role`, `refresh_token`, `password_reset_token`, `auth_event`. Ajout de `created_by` / `updated_by` aux tables existantes, et alimentation de `audit_log.user_id`.

## Décision prise : identifiant de connexion

**Nom d'utilisateur, email facultatif** (validé le 2026-10-01).

- Identifiant : nom d'utilisateur unique, en minuscules, 3 à 50 caractères (`a-z`, `0-9`, `.`, `_`, `-`), ex. `a.diallo`.
- À la création d'un compte et à chaque réinitialisation par un administrateur, le système génère un **mot de passe temporaire**, affiché une seule fois à l'administrateur.
- Un mot de passe temporaire doit être changé à la première connexion : tant que ce n'est pas fait, toute requête autre que le changement de mot de passe, la consultation du profil et la déconnexion reçoit 403 `PASSWORD_CHANGE_REQUIRED`.
- « Mot de passe oublié » accepte le nom d'utilisateur ou l'email ; l'email de réinitialisation n'est envoyé que si le compte a une adresse email. Sans email, l'employé s'adresse à son administrateur (`POST /users/{id}/reset-password`).
