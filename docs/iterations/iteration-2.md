# Itération 2 — Paramétrage de l'hôtel, chambres, statuts et blocages

**Statut** : réalisé le 2026-10-01
**Références** : cahier des charges §6, §8.3, §9, §12.1, §12.5, §20.1, §33.2, §44.1

## Objectif

À la fin de l'itération, un hôtel est entièrement paramétré dans le PMS : ses informations légales, ses règles d'exploitation, ses taxes, ses moyens de paiement, ses canaux de réservation et ses motifs. Son parc de chambres est décrit (types et chambres) avec les trois dimensions de statut, l'historique de chaque changement et les blocages datés. Tout est prêt pour la tarification et les réservations (itérations 3 et 4).

## Périmètre

| Inclus | Reporté |
| --- | --- |
| Création d'un hôtel par le super administrateur, modification de ses informations | Clôture et passage de la date d'exploitation (itération 8) |
| Paramètres d'exploitation (heures, politiques, prix TTC/HT, garanties, plafonds de remise) | Application des politiques d'annulation et de garantie (itérations 4 et 6) |
| Taxes avec historique des taux et exonérations | Calcul des taxes sur les charges (itérations 6 et 8) |
| Moyens de paiement, canaux de réservation, motifs | Saisie des paiements (itération 7) |
| Types de chambres (capacité, prix de base, équipements, photos) | Grilles tarifaires (itération 3) |
| Chambres, trois dimensions de statut, historique | Tâches de ménage (MVP 2), tickets de maintenance (MVP 3) |
| Mise à jour manuelle du statut ménage | Passage automatique à `DIRTY` au check-out (itération 8) |
| Blocages de chambres datés (maintenance, hors service, usage interne) | Contrôle des réservations en conflit avec un blocage (itération 4, quand les réservations existeront) |
| Écrans : paramètres de l'hôtel, chambres, état des chambres | |

## Décisions de conception

| Sujet | Décision |
| --- | --- |
| Hôtels | Le super administrateur crée un hôtel (code unique dans l'organisation, devise, fuseau horaire, date d'exploitation initiale). La création applique un paramétrage par défaut : moyens de paiement, canaux, motifs, paramètres d'exploitation. |
| Paramètres d'exploitation | Stockés par hôtel sous forme typée (un objet validé, pas de clés libres) : prix TTC ou HT, heures de check-in/check-out, inspection obligatoire, statuts ménage autorisés au check-in, pièce d'identité obligatoire, garantie exigée, paiement du walk-in, plafonds de remise par rôle, politique d'annulation par défaut. |
| Taxes | Trois types (§8.3) : `PERCENTAGE`, `FIXED_PER_PERSON_NIGHT`, `FIXED_PER_UNIT`. Chaque taxe s'applique à des catégories de charge (hébergement, restauration, boissons, services, autres), a une période de validité et des exonérations (âge, résidents). Un changement de taux crée une nouvelle période : l'ancien taux reste en historique, jamais modifié. |
| Montants | `NUMERIC(18,2)` et `BigDecimal` ; devise et nombre de décimales selon la norme ISO 4217 (XOF : 0). |
| Moyens de paiement | Code, libellé, type (`CASH`, `CARD`, `TRANSFER`, `CHEQUE`, `MOBILE_MONEY`, `OTHER`), passage par la caisse, référence obligatoire, actif. Les espèces passent toujours par la caisse. Pas de suppression : désactivation. |
| Statuts de chambre | Trois dimensions indépendantes (§9.3) : occupation `VACANT`/`OCCUPIED` (modifiée seulement par le check-in/check-out), ménage `DIRTY`/`CLEANING`/`CLEAN`/`INSPECTED` (modifiable à la main), disponibilité technique `IN_SERVICE`/`MAINTENANCE`/`OUT_OF_ORDER` (calculée à partir des blocages actifs à la date d'exploitation). |
| Historique | Chaque changement de statut ménage, chaque blocage créé ou levé est enregistré dans `room_status_history` (dimension, ancienne et nouvelle valeur, motif, auteur, date). |
| Blocages | Période `[début, fin[` obligatoire. Deux blocages d'une même chambre ne peuvent pas se chevaucher : contrainte d'exclusion PostgreSQL (§13.2), comme pour les réservations à venir. Un blocage commencé peut être levé (fin ramenée à la date d'exploitation) ; un blocage futur peut être supprimé. |
| Suppression | Les référentiels ne sont jamais supprimés physiquement : désactivation (types de chambres, chambres, moyens de paiement, canaux, motifs). |
| Audit | Toutes les créations, modifications, désactivations et changements de statut sont audités, rattachés à l'hôtel. |

## User stories

### US-HOTEL-01 — Créer un hôtel

En tant que super administrateur, je veux ajouter un hôtel à l'organisation pour le gérer dans le PMS.

- Code unique dans l'organisation (2 à 10 caractères, majuscules et chiffres), nom, devise (XOF par défaut), fuseau horaire (Africa/Dakar par défaut), langue, date d'exploitation initiale → 201, hôtel créé avec le paramétrage par défaut.
- Code déjà utilisé → 409 `HOTEL_CODE_ALREADY_EXISTS`.
- Devise ou fuseau horaire inconnus → 400 `VALIDATION_ERROR`.
- Réservé au super administrateur → 403 pour tout autre profil.

### US-HOTEL-02 — Modifier les informations de l'hôtel

En tant que directeur (`HOTEL_UPDATE`), je veux tenir à jour les informations qui figurent sur les factures.

- Nom commercial, raison sociale, adresse, téléphone, email, identifiants légaux (libellé + valeur : RCCM, NINEA, IFU…), logo.
- Devise et date d'exploitation non modifiables ici (la date n'avance qu'à la clôture journalière).
- Logo : PNG ou JPEG, 500 Ko maximum (voir décision à prendre).
- Modification concurrente → 409 `VERSION_CONFLICT`. Chaque modification est auditée.

### US-HOTEL-03 — Paramètres d'exploitation

En tant que directeur (`SETTINGS_UPDATE`), je veux définir les règles de mon hôtel.

| Paramètre | Valeur par défaut |
| --- | --- |
| Prix affichés | TTC |
| Heure de check-in / check-out | 14:00 / 12:00 |
| Inspection obligatoire avant check-in | Non |
| Pièce d'identité obligatoire pour les adultes | Oui |
| Garantie exigée au check-in | Première nuit |
| Paiement de la première nuit pour un walk-in | Oui |
| Politique d'annulation par défaut | Gratuite jusqu'à 24 h avant l'arrivée, puis 1 nuit ; no-show : 1 nuit |
| Plafonds de remise par rôle | Réceptionniste 10 %, directeur 30 % |

- Valeurs hors bornes (heure invalide, pourcentage > 100) → 400 avec le champ concerné.
- L'audit indique les paramètres modifiés, avant et après.

### US-TAX-01 — Gérer les taxes

En tant que directeur (`TAX_MANAGE`), je veux paramétrer les taxes applicables.

- Création : code, libellé, type, taux (pourcentage) ou montant (forfaitaire), TVA oui/non, catégories de charge concernées, date de début, exonérations (enfants de moins de N ans, résidents).
- Changement de taux : saisie d'un nouveau taux avec sa date d'effet → la période précédente se termine la veille ; une période passée ne peut plus être modifiée → 409 `TAX_PERIOD_LOCKED`.
- Deux périodes d'une même taxe ne peuvent pas se chevaucher.
- La liste indique pour chaque taxe le taux en vigueur à la date d'exploitation.

### US-PAY-01 — Gérer les moyens de paiement

En tant que directeur (`PAYMENT_METHOD_MANAGE`), je veux choisir les moyens de paiement acceptés.

- Moyens créés par défaut : Espèces, Carte bancaire, Virement, Chèque, Orange Money, Wave (actifs) ; MTN MoMo, Moov Money (créés désactivés). D'autres opérateurs locaux peuvent être ajoutés.
- Création et modification : code unique dans l'hôtel, libellé, type, référence obligatoire (vrai par défaut pour chèque, carte, virement et Mobile Money), passage par la caisse (toujours vrai pour les espèces → 422 `CASH_REQUIRES_CASH_SESSION` sinon).
- Désactivation et réactivation ; un moyen désactivé n'est plus proposé à l'encaissement.

### US-REF-01 — Canaux de réservation et motifs

En tant que directeur (`SETTINGS_UPDATE`), je veux adapter les listes utilisées par la réception.

- Canaux par défaut : Réception (walk-in), Téléphone, Email, Site web, Booking.com, Expedia, Agence, Entreprise ; commission en pourcentage facultative.
- Motifs par domaine : annulation, remise, changement de chambre, blocage, ajustement, mise à jour de statut. Listes par défaut fournies, modifiables.
- Code unique par hôtel et par domaine ; désactivation plutôt que suppression.

### US-ROOM-01 — Types de chambres

En tant que directeur (`ROOM_CREATE`, `ROOM_UPDATE`), je veux décrire mes catégories de chambres.

- Code (unique dans l'hôtel), nom, description, occupation de base, capacité maximale, adultes max, enfants max, prix de base (devise de l'hôtel), équipements (liste paramétrable), ordre d'affichage.
- Contrôles : occupation de base ≤ capacité ≤ adultes max + enfants max ; prix ≥ 0 → sinon 400.
- Désactivation impossible tant que des chambres actives sont de ce type → 409 `ROOM_TYPE_IN_USE`.

### US-ROOM-02 — Chambres

En tant que directeur, je veux enregistrer chaque chambre.

- Numéro (unique dans l'hôtel), étage, bâtiment / aile, type, caractéristiques (vue, accès PMR, communicante avec…), observations.
- Création possible en série : de 101 à 120, étage 1, type Standard.
- Une chambre créée est `VACANT` / `CLEAN` / `IN_SERVICE`.
- Numéro déjà utilisé → 409 `ROOM_NUMBER_ALREADY_EXISTS`.
- Désactivation d'une chambre occupée → 409 `ROOM_OCCUPIED`.

### US-ROOM-03 — Consulter l'état des chambres

En tant qu'employé (`ROOM_VIEW`), je veux voir l'état de toutes les chambres.

- Vue en grille (tuiles par étage) et en liste, avec le statut synthétique (« Libre – Propre », « Libre – Sale », « Occupée – Sale », « Maintenance », « Hors service »…) et un code couleur.
- Filtres : étage, type, occupation, ménage, disponibilité technique.
- Compteurs en tête : libres et propres, sales, en nettoyage, occupées, maintenance, hors service.

### US-ROOM-04 — Mettre à jour le statut ménage

En tant qu'agent ou responsable housekeeping (`HK_STATUS_UPDATE`), je veux indiquer l'état de propreté d'une chambre.

- Transitions autorisées : `DIRTY → CLEANING → CLEAN → INSPECTED`, et retour à `DIRTY` depuis tout statut (problème constaté, motif obligatoire).
- `INSPECTED` exige `HK_INSPECT` ; si l'inspection n'est pas obligatoire (paramètre), `CLEAN` suffit pour la vente.
- Transition non autorisée → 409 `INVALID_HOUSEKEEPING_TRANSITION`.
- Chaque changement est historisé ; l'historique d'une chambre est consultable.

### US-ROOM-05 — Bloquer une chambre

En tant que responsable maintenance ou directeur (`ROOM_BLOCK_MANAGE`), je veux retirer une chambre de la vente sur une période.

- Type (`MAINTENANCE`, `OUT_OF_ORDER`, `HOUSE_USE`), date de début, date de fin (obligatoire, même estimée), motif.
- Fin ≤ début → 400. Chevauchement avec un autre blocage de la chambre → 409 `ROOM_BLOCK_OVERLAP`.
- Un blocage en cours à la date d'exploitation rend la chambre `MAINTENANCE` ou `OUT_OF_ORDER` ; `HOUSE_USE` la laisse en service mais hors vente.
- Levée d'un blocage en cours (fin ramenée à la date d'exploitation) et suppression d'un blocage futur ; tout est historisé.

## Écrans

| Écran | Contenu |
| --- | --- |
| Paramètres de l'hôtel | Onglets : informations (avec logo), exploitation, taxes, moyens de paiement, canaux, motifs |
| Chambres | Onglets : état des chambres (grille / liste, filtres, compteurs, changement de statut ménage, historique), types de chambres, gestion des chambres (création en série), blocages |
| Administration | Création d'un hôtel (super administrateur) |

## Données

Migration V7 : `hotel_setting` (paramètres typés en JSON validé), `tax` + `tax_rate_period`, `payment_method`, `reservation_channel`, `reason_code`, `amenity`, `room_type`, `room_type_amenity`, `room`, `room_status_history`, `room_block` (contrainte d'exclusion sur la chambre et la période).

## Décisions prises (2026-10-01)

1. **Fichiers en base pour l'instant** : logo de l'hôtel et photos des types de chambres stockés en base (PNG ou JPEG, 500 Ko maximum chacun). Un stockage objet chiffré sera mis en place à l'itération 6 pour les pièces d'identité.
2. **Taxes de démonstration provisoires** : TVA 18 % sur toutes les catégories ; taxe de séjour de 1 000 XOF par adulte et par nuit, moins de 12 ans exonérés. À confirmer avec le comptable (point ouvert P3). Un nouvel hôtel est créé sans taxe : elles se paramètrent dans l'écran des taxes.
3. **Mobile Money** : Orange Money et Wave actifs ; MTN MoMo et Moov Money créés désactivés.

## Réalisation

- Backend : module `hotel` (création d'hôtel, informations, logo, paramètres d'exploitation typés ; sous-package `configuration` : taxes et historique des taux, moyens de paiement, canaux, motifs, paramétrage par défaut) et nouveau module `rooms` (équipements, types et photos, chambres, statuts, historique, blocages) ; migration V7.
- Frontend : Administration › Paramètres de l'hôtel (informations et logo, exploitation, taxes, moyens de paiement, canaux, motifs) et › Hôtels (super administrateur) ; page Chambres (état par étage avec compteurs et actions de ménage, types, gestion et création en série, blocages).
- Tests : 74 tests backend, 22 tests frontend ; parcours vérifié dans le navigateur.
- Écarts :
  - Les taux et montants de taxes sont renvoyés avec deux décimales ; le format définitif des montants (nombre ou chaîne, §34.1) sera fixé avec le module de facturation.
  - Le contrôle des réservations en conflit avec un blocage arrivera avec les réservations (itération 4).
