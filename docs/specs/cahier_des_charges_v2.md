# CAHIER DES CHARGES FONCTIONNEL ET TECHNIQUE

## Système intégré de gestion hôtelière — PMS

| | |
|---|---|
| **Version** | 2.0 |
| **Date** | 2026-10-01 |
| **Statut** | Version de travail — hypothèses à valider (voir §0.3) |
| **Type de projet** | Application web de gestion hôtelière (Property Management System) |
| **Architecture cible** | Monolithe modulaire, API REST, multi-hôtel natif |
| **Périmètre initial** | Un établissement hôtelier, architecture prête pour le multi-hôtel |

---

# 0. SUIVI DU DOCUMENT

## 0.1 Historique

| Version | Date | Objet |
|---|---|---|
| 1.0 | — | Version initiale (périmètre, modules, principes) |
| 2.0 | 2026-10-01 | Ajout des règles métier, du modèle de données détaillé, des décisions d'architecture, de la clôture journalière, du folio, refonte des statuts chambre, correction du phasage MVP. Backend : Java / Spring Boot |

## 0.2 Principales évolutions par rapport à la v1.0

1. **Statuts chambre** : séparation en trois dimensions indépendantes (occupation / ménage / disponibilité technique). Suppression du statut `RESERVED` au niveau chambre (§9.3).
2. **Folio (compte séjour)** : ajout de l'entité centrale qui reçoit toutes les charges et les paiements ; la facture est émise à partir du folio (§15).
3. **Clôture journalière (night audit)** et **date d'exploitation** : ajout complet (§17).
4. **Réservation multi-chambres** : une réservation contient une ou plusieurs lignes de chambre ; gestion des réservations par type de chambre et attribution (§12).
5. **Règles de calcul** formalisées : nuitées, résolution du tarif, taxes (dont taxe de séjour forfaitaire), remises, arrondis XOF (§8).
6. **Facturation** : numérotation légale continue, immuabilité, avoirs, mentions légales (§19).
7. **Caisse** : distinction caisse / session de caisse ; règles des paiements non espèces (§21).
8. **Modèle de données** détaillé avec attributs clés et contraintes, entités manquantes ajoutées (§33).
9. **Données personnelles** et **fiche de police** (§36).
10. **Exigences non fonctionnelles chiffrées** (§38).
11. **Phasage MVP corrigé** : audit, caisse simplifiée, services simples, email transactionnel et clôture journalière intégrés au MVP 1 (§44).
12. **Choix techniques** proposés (stack, multi-tenant, concurrence, idempotence) sous forme de décisions d'architecture (§5).

## 0.3 Hypothèses retenues (à valider)

Les points suivants n'ont pas encore été arbitrés. Le document retient une valeur par défaut, modifiable sans remettre en cause la conception.

| # | Sujet | Hypothèse retenue | Impact si changée |
|---|---|---|---|
| H1 | Stack backend | **Java 21 / Spring Boot 4.1** — **décision validée** (Spring Boot 3 n'est plus supporté, voir docs/adr/0001) | — |
| H2 | Hébergement | **Cloud** (VPS ou cloud managé), déploiement sur site possible avec les mêmes conteneurs | Faible |
| H3 | Coupures internet | **Mode dégradé** (rapport de secours + consultation en cache), pas de synchronisation hors ligne complète | Fort si un vrai mode hors ligne est exigé |
| H4 | Pays de référence | **Sénégal** (XOF, TVA paramétrable, taxe de séjour forfaitaire, fiche de police). Tout reste paramétrable par hôtel | Faible |
| H5 | Langues | Français au MVP 1, anglais en MVP 2 (i18n prévue dès le départ) | Faible |
| H6 | Taille | Jusqu'à 20 hôtels, 300 chambres par hôtel, 50 utilisateurs simultanés par hôtel | Moyen |
| H7 | Surréservation | **Interdite** au MVP (paramètre prévu pour plus tard) | Moyen |
| H8 | Prix affichés | **TTC** par défaut (paramètre par hôtel : TTC ou HT) | Faible |

---

# 1. CONTEXTE DU PROJET

Les établissements hôteliers gèrent simultanément de nombreuses activités : réservations, disponibilité des chambres, accueil, check-in et check-out, facturation, paiements, restauration, services additionnels, housekeeping, maintenance, stocks, dépenses, caisse, reporting, gestion du personnel et des accès.

Lorsque ces activités sont gérées manuellement ou avec des outils indépendants, cela entraîne :

* des erreurs de réservation (doubles attributions) ;
* des difficultés de suivi des chambres ;
* des erreurs de facturation ;
* des écarts de caisse ;
* un manque de traçabilité ;
* une faible visibilité sur les performances ;
* des difficultés de consolidation.

Le projet vise à mettre en place un **Property Management System (PMS)** centralisant l'ensemble des opérations de l'hôtel, adapté au marché d'Afrique de l'Ouest.

---

# 2. OBJECTIFS

## 2.1 Objectif général

Gérer de manière centralisée, fiable et sécurisée l'ensemble du cycle de vie d'un séjour hôtelier :

**Réservation → Arrivée → Check-in → Séjour → Consommations → Clôture journalière → Check-out → Facturation → Paiement → Reporting**

## 2.2 Objectifs spécifiques

Le système doit permettre de gérer : les chambres et leurs statuts, les disponibilités, les tarifs, les réservations, les clients et entreprises, les séjours et leurs comptes (folios), le check-in et le check-out, la clôture journalière, les paiements, la facturation, la caisse, les services, le restaurant/bar, le housekeeping, la maintenance, les stocks, les fournisseurs, les dépenses, le reporting, les accès utilisateurs et la traçabilité des opérations sensibles.

## 2.3 Questions auxquelles le système doit répondre

* Combien de chambres sont disponibles / occupées / en nettoyage / hors service ?
* Qui arrive et qui part aujourd'hui ?
* Quel est le chiffre d'affaires du jour, du mois ? Combien a été encaissé ?
* Quel est le montant des impayés ?
* Quelles chambres doivent être nettoyées ? Lesquelles sont en maintenance ?
* Quels services génèrent le plus de revenus ?
* Quelle est la situation de chaque caisse ?
* Quelles sont les dépenses ?
* Quel est l'historique d'un client ?
* Qui a effectué une modification donnée, quand et avec quelles valeurs ?

---

# 3. PÉRIMÈTRE

## 3.1 Modules inclus

| # | Module | Phase |
|---|---|---|
| 1 | Authentification | MVP 1 |
| 2 | Utilisateurs, rôles, permissions | MVP 1 |
| 3 | Organisation et hôtel (paramétrage) | MVP 1 |
| 4 | Chambres et types de chambres | MVP 1 |
| 5 | Blocages de chambres | MVP 1 |
| 6 | Clients | MVP 1 |
| 7 | Entreprises | MVP 1 (simple) / MVP 3 (avancé) |
| 8 | Tarification | MVP 1 (simple) / MVP 3 (avancé) |
| 9 | Réservations | MVP 1 |
| 10 | Planning | MVP 1 |
| 11 | Check-in | MVP 1 |
| 12 | Séjour et folio | MVP 1 |
| 13 | Check-out | MVP 1 |
| 14 | Clôture journalière (night audit) | MVP 1 |
| 15 | Facturation | MVP 1 |
| 16 | Paiements | MVP 1 |
| 17 | Caisse | MVP 1 (simplifiée) / MVP 2 (complète) |
| 18 | Services additionnels | MVP 1 (catalogue simple) / MVP 2 |
| 19 | Housekeeping | MVP 1 (statuts) / MVP 2 (tâches) |
| 20 | Audit | MVP 1 |
| 21 | Dashboard | MVP 1 |
| 22 | Reporting | MVP 1 (opérationnel) / MVP 2 (complet) |
| 23 | Notifications | MVP 1 (email transactionnel) / MVP 2 (internes) |
| 24 | Dépenses | MVP 2 |
| 25 | Promotions | MVP 2 |
| 26 | Réclamations | MVP 2 |
| 27 | Restaurant / bar | MVP 3 |
| 28 | Stocks | MVP 3 |
| 29 | Fournisseurs et achats | MVP 3 |
| 30 | Maintenance (tickets, équipements) | MVP 3 |

## 3.2 Évolutions futures (hors périmètre de ce document)

Portail client, réservation en ligne, intégration OTA (Booking.com, Expedia…), channel manager, paiement Mobile Money et carte intégrés, SMS, WhatsApp, comptabilité, paie, fidélité, application mobile, événements et salles de conférence, BI.

L'architecture doit permettre ces évolutions sans refonte (couches d'abstraction pour les paiements et les notifications, API documentée, événements métier).

## 3.3 Exclusions explicites

* Synchronisation hors ligne complète (voir H3).
* Surréservation (voir H7).
* Comptabilité générale (seuls des exports comptables sont prévus).
* Intégration matérielle (serrures électroniques, TPE, standard téléphonique) : prévue en phase ultérieure via interfaces.

---

# 4. GLOSSAIRE

| Terme | Définition |
|---|---|
| **Réservation** | Dossier commercial regroupant une ou plusieurs **lignes de chambre**, rattaché à un client titulaire (et éventuellement une entreprise). |
| **Ligne de réservation** | Une chambre (ou un type de chambre) pour une période donnée, avec ses occupants et ses prix par nuit. Chaque ligne devient un séjour au check-in. |
| **Séjour (Stay)** | Occupation effective d'une chambre, créée au check-in et clôturée au check-out. |
| **Folio** | Compte financier qui reçoit les **charges** (nuitées, services, consommations, taxes) et les **paiements**. Solde = charges − paiements. |
| **Charge** | Ligne débitrice d'un folio (nuitée, service, consommation, pénalité, ajustement). |
| **Date d'exploitation (business date)** | Date comptable de l'hôtel. Elle ne change qu'à la clôture journalière, indépendamment de l'heure réelle. |
| **Clôture journalière (night audit)** | Traitement qui clôture la date d'exploitation : no-shows, enregistrement des nuitées, contrôles, rapports, passage au jour suivant. |
| **Nuitée** | Une nuit facturée pour une chambre. |
| **Walk-in** | Client se présentant sans réservation. |
| **No-show** | Client ne s'étant pas présenté à la date d'arrivée prévue. |
| **Option** | Réservation non garantie (`PENDING`) avec une date d'expiration. |
| **Blocage** | Retrait d'une chambre de la vente sur une période (maintenance, hors service, usage interne). |
| **Avoir** | Document annulant tout ou partie d'une facture émise. |
| **Session de caisse** | Période entre l'ouverture et la clôture d'une caisse par un utilisateur. |
| **Main courante** | Journal quotidien de toutes les charges et paiements par date d'exploitation. |
| **Fiche de police** | Fiche d'enregistrement réglementaire des clients hébergés. |
| **TO** | Taux d'occupation. **ADR** : prix moyen par chambre vendue. **RevPAR** : revenu hébergement par chambre disponible. |

---

# 5. DÉCISIONS D'ARCHITECTURE

Chaque décision fera l'objet d'un document ADR (Architecture Decision Record) dans le dépôt de code.

| # | Décision | Justification |
|---|---|---|
| ADR-01 | **Monolithe modulaire** (un backend, modules métier isolés) | Périmètre maîtrisable par une petite équipe, transactions simples, découpage futur possible |
| ADR-02 | **Frontend React + TypeScript**, **backend Java 21 + Spring Boot 4.1** (H1) | Écosystème mature pour les applications transactionnelles (Spring Security, JPA, transactions déclaratives) ; client TypeScript généré depuis le contrat OpenAPI pour garder un typage partagé |
| ADR-03 | **PostgreSQL** comme unique base transactionnelle | Contraintes d'exclusion, transactions, JSONB, RLS |
| ADR-04 | **Multi-hôtel natif** : `organization_id` et `hotel_id` sur toutes les tables métier, filtrage automatique + Row Level Security PostgreSQL en défense en profondeur | Isolation des données dès le premier jour |
| ADR-05 | **Anti-double réservation garanti par la base** : contrainte d'exclusion sur (chambre, période) | Seule garantie fiable en cas d'accès concurrents |
| ADR-06 | **Montants en `NUMERIC(18,2)`** + code devise ISO 4217 ; arrondi selon la devise (XOF : 0 décimale) | Pas de flottants pour l'argent |
| ADR-07 | **Idempotence** via l'en-tête `Idempotency-Key` sur les opérations critiques | Pas de double paiement / double réservation |
| ADR-08 | **Verrouillage optimiste** (colonne `version`) sur les entités modifiables | Détection des modifications concurrentes (HTTP 409) |
| ADR-09 | **Pattern Outbox** pour les événements métier (notifications, intégrations), via le registre de publication d'événements de Spring Modulith | Cohérence entre transaction et envoi |
| ADR-10 | **Documents financiers immuables** : jamais supprimés ni modifiés après émission ; corrections par avoir ou écriture inverse | Conformité et traçabilité |
| ADR-11 | **Suppression logique** (`deleted_at`) pour les référentiels ; aucune suppression pour les données financières | Traçabilité |
| ADR-12 | **Stockage des fichiers** (logos, pièces d'identité, PDF) sur un stockage objet compatible S3 (MinIO en local) | Découplage, sauvegarde séparée |
| ADR-13 | **Tâches asynchrones et planifiées** (emails, PDF lourds, rapports, night audit automatique) via une file de jobs persistée dans PostgreSQL (JobRunr), verrou ShedLock pour les tâches planifiées | Réactivité de l'API, pas d'infrastructure supplémentaire obligatoire |
| ADR-14 | **API versionnée** `/api/v1`, documentée en OpenAPI (springdoc-openapi), client TypeScript généré pour le frontend | API First |
| ADR-15 | **Frontières de modules vérifiées** par Spring Modulith (tests de structure) | Empêcher le couplage entre modules dans le monolithe |

---

# 6. PROFILS UTILISATEURS

| Code | Profil | Périmètre |
|---|---|---|
| SA | Super administrateur | Toute l'organisation : hôtels, utilisateurs, rôles, paramétrage global, audit |
| DIR | Directeur / manager | Son ou ses hôtels : supervision complète, tarifs, rapports, validations |
| REC | Réceptionniste | Réservations, clients, planning, check-in/out, folios, paiements, factures |
| CAI | Caissier | Encaissements, sessions de caisse, remboursements selon autorisation |
| CPT | Comptable | Factures, avoirs, paiements, dépenses, fournisseurs, rapports financiers, exports |
| RHK | Responsable housekeeping | Statuts ménage, tâches, affectations, inspections |
| AHK | Agent housekeeping | Ses tâches, mise à jour du statut ménage, signalements |
| RMT | Responsable maintenance | Tickets, équipements, blocages maintenance |
| RRS | Responsable restaurant | Points de vente, tables, produits, commandes, additions, transfert en chambre |
| AUD | Auditeur de nuit | Clôture journalière, contrôles, rapports de nuit (souvent cumulé avec REC) |

Les rôles sont **paramétrables** : les profils ci-dessus sont des rôles par défaut créés à l'initialisation. Un utilisateur peut avoir des rôles différents selon l'hôtel (§7.4).

---

# 7. AUTHENTIFICATION ET CONTRÔLE D'ACCÈS

## 7.1 Fonctionnalités

* Connexion / déconnexion.
* Modification du mot de passe.
* Réinitialisation du mot de passe par email (lien à usage unique, valable 30 minutes).
* Consultation et révocation de ses sessions actives.
* Gestion de son profil (nom, langue, téléphone).
* Sélection de l'hôtel actif si l'utilisateur a accès à plusieurs hôtels.

## 7.2 Règles de sécurité

| Règle | Valeur par défaut (paramétrable) |
|---|---|
| Hachage des mots de passe | Argon2id |
| Politique de mot de passe | 10 caractères minimum, non présent dans une liste de mots de passe compromis |
| Jeton d'accès (JWT) | Durée de vie 15 minutes |
| Jeton de rafraîchissement | 7 jours, rotation à chaque usage, stocké haché en base, cookie `httpOnly` `Secure` `SameSite=Strict` ; réutilisation d'un jeton révoqué → révocation de toute la famille de sessions |
| Inactivité | Déconnexion après 30 minutes sans activité |
| Tentatives de connexion | 5 échecs → blocage du compte 15 minutes ; journalisation de chaque tentative |
| Double authentification | Optionnelle (MVP 2), recommandée pour SA et DIR |
| Journalisation | Connexions, échecs, déconnexions, réinitialisations, changements de rôle |

## 7.3 Modèle RBAC

* **Permission** : code `MODULE_ACTION` (ex. `RESERVATION_CREATE`).
* **Rôle** : ensemble de permissions, défini au niveau de l'organisation.
* **Affectation** : (utilisateur, hôtel, rôle). Un utilisateur n'accède qu'aux hôtels pour lesquels il a une affectation.
* **Super administrateur** : indicateur sur l'utilisateur, accès à tous les hôtels de l'organisation.
* Les permissions sont contrôlées **dans l'API (obligatoire)** et **dans le frontend (confort d'affichage uniquement)**. Le frontend n'est jamais une couche de sécurité.
* Le jeton d'accès contient l'identifiant utilisateur ; les permissions de l'hôtel actif sont chargées côté serveur (cache invalidé à chaque modification de rôle).

## 7.4 Catalogue des permissions

| Module | Permissions |
|---|---|
| Hôtel / paramétrage | `HOTEL_VIEW`, `HOTEL_UPDATE`, `SETTINGS_UPDATE`, `TAX_MANAGE`, `PAYMENT_METHOD_MANAGE` |
| Utilisateurs | `USER_VIEW`, `USER_CREATE`, `USER_UPDATE`, `USER_DISABLE`, `ROLE_MANAGE` |
| Chambres | `ROOM_VIEW`, `ROOM_CREATE`, `ROOM_UPDATE`, `ROOM_BLOCK_MANAGE` |
| Clients | `CUSTOMER_VIEW`, `CUSTOMER_CREATE`, `CUSTOMER_UPDATE`, `CUSTOMER_MERGE`, `CUSTOMER_ID_DOCUMENT_VIEW` |
| Entreprises | `COMPANY_VIEW`, `COMPANY_MANAGE`, `COMPANY_CREDIT_OVERRIDE` |
| Tarifs | `RATE_VIEW`, `RATE_MANAGE`, `RATE_OVERRIDE` (prix manuel) |
| Promotions | `PROMOTION_MANAGE` |
| Réservations | `RESERVATION_VIEW`, `RESERVATION_CREATE`, `RESERVATION_UPDATE`, `RESERVATION_CANCEL`, `RESERVATION_NO_SHOW`, `RESERVATION_REINSTATE` |
| Séjours | `CHECKIN_CREATE`, `CHECKIN_OVERRIDE_HOUSEKEEPING`, `CHECKOUT_CREATE`, `CHECKOUT_WITH_BALANCE`, `STAY_ROOM_CHANGE`, `STAY_EXTEND` |
| Folios | `FOLIO_VIEW`, `FOLIO_CHARGE_POST`, `FOLIO_CHARGE_CORRECT`, `FOLIO_TRANSFER` |
| Remises | `DISCOUNT_APPLY`, `DISCOUNT_ABOVE_LIMIT` |
| Facturation | `INVOICE_VIEW`, `INVOICE_ISSUE`, `INVOICE_PROFORMA`, `CREDIT_NOTE_ISSUE`, `INVOICE_SEND` |
| Paiements | `PAYMENT_VIEW`, `PAYMENT_CREATE`, `PAYMENT_CANCEL`, `PAYMENT_REFUND` |
| Caisse | `CASH_SESSION_OPEN`, `CASH_SESSION_CLOSE`, `CASH_MOVEMENT_CREATE`, `CASH_ADJUSTMENT`, `CASH_SUPERVISE` |
| Clôture journalière | `NIGHT_AUDIT_RUN`, `NIGHT_AUDIT_VIEW` |
| Housekeeping | `HK_VIEW`, `HK_STATUS_UPDATE`, `HK_TASK_ASSIGN`, `HK_INSPECT` |
| Maintenance | `MAINTENANCE_VIEW`, `MAINTENANCE_TICKET_CREATE`, `MAINTENANCE_TICKET_MANAGE` |
| Restaurant | `POS_VIEW`, `POS_ORDER_CREATE`, `POS_ORDER_VOID`, `POS_TRANSFER_TO_ROOM`, `POS_PRODUCT_MANAGE` |
| Stocks | `STOCK_VIEW`, `STOCK_MOVEMENT_CREATE`, `STOCK_INVENTORY`, `STOCK_ADJUST` |
| Fournisseurs | `SUPPLIER_MANAGE`, `PURCHASE_MANAGE`, `SUPPLIER_PAYMENT_CREATE` |
| Dépenses | `EXPENSE_CREATE`, `EXPENSE_VALIDATE`, `EXPENSE_VIEW` |
| Rapports | `DASHBOARD_VIEW`, `REPORT_OPERATIONS_VIEW`, `REPORT_FINANCE_VIEW`, `REPORT_EXPORT` |
| Audit | `AUDIT_VIEW` |

## 7.5 Matrice des rôles par défaut

Légende : **L** = lecture, **E** = écriture courante, **V** = actions sensibles / validation, **–** = aucun accès.

| Module | SA | DIR | REC | CAI | CPT | RHK | AHK | RMT | RRS | AUD |
|---|---|---|---|---|---|---|---|---|---|---|
| Paramétrage / utilisateurs | V | E | – | – | – | – | – | – | – | – |
| Chambres / blocages | V | V | L | – | – | L | L | E | – | L |
| Clients | V | V | E | L | L | – | – | – | L | L |
| Entreprises | V | V | L | – | E | – | – | – | – | L |
| Tarifs | V | V | L | – | L | – | – | – | – | L |
| Réservations / planning | V | V | E | L | L | L | – | L | – | E |
| Check-in / check-out | V | V | E | – | – | – | – | – | – | E |
| Folios / charges | V | V | E | E | L | – | – | – | E | E |
| Remises au-delà du plafond | V | V | – | – | – | – | – | – | – | – |
| Facturation / avoirs | V | V | E | E | V | – | – | – | – | E |
| Paiements | V | V | E | E | L | – | – | – | E | E |
| Remboursements | V | V | – | V | – | – | – | – | – | – |
| Caisse | V | V | E | E | L | – | – | – | E | E |
| Clôture journalière | V | V | – | – | L | – | – | – | – | V |
| Housekeeping | V | V | L | – | – | V | E | L | – | L |
| Maintenance | V | V | E | – | – | E | E | V | – | – |
| Restaurant | V | V | L | E | L | – | – | – | V | – |
| Stocks / fournisseurs | V | V | – | – | E | – | – | L | E | – |
| Dépenses | V | V | – | E | V | – | – | E | E | – |
| Rapports | V | V | L (opé.) | L (caisse) | V | L (HK) | – | L (maint.) | L (resto) | L |
| Audit | V | L | – | – | L | – | – | – | – | – |

---

# 8. RÈGLES DE CALCUL

Toutes les règles de calcul sont implémentées dans le **domaine backend** (fonctions pures testées unitairement). Le frontend n'affiche que les montants calculés par le serveur.

## 8.1 Nuitées

* **Nombre de nuits** = date de départ − date d'arrivée (en jours calendaires, dates sans heure).
* Une ligne de réservation a au minimum 1 nuit, sauf **location à la journée (day use)** : arrivée = départ, 0 nuit, facturée selon le tarif day use du plan tarifaire.
* Heures standards paramétrables par hôtel : check-in à partir de 14:00, check-out avant 12:00.
* **Arrivée anticipée / départ tardif** : au MVP 1, facturés manuellement via des services prédéfinis (« Early check-in », « Late check-out »). En MVP 2, règles automatiques paramétrables (ex. départ entre 12:00 et 18:00 = 50 % de la nuit, après 18:00 = nuit complète).
* Les nuitées sont **portées au folio par la clôture journalière**, une charge par nuit, au prix figé de la nuit (§8.2).

## 8.2 Résolution du prix d'une nuit

Lors de la création ou de la modification d'une ligne, le serveur calcule un **prix par nuit** (`ReservationNight`) et le **fige**. Ordre de priorité :

1. **Prix manuel** saisi par un utilisateur ayant `RATE_OVERRIDE` (motif obligatoire, audité).
2. **Tarif négocié de l'entreprise** si la réservation est rattachée à une entreprise ayant un plan tarifaire actif pour ce type de chambre et cette date.
3. **Plan tarifaire choisi** (ex. « Long séjour », « Week-end ») si ses conditions sont remplies (nombre de nuits minimum, jours de la semaine, période).
4. **Plan tarifaire standard** de l'hôtel pour ce type de chambre et cette date (saison, jour de la semaine).
5. **Prix de base du type de chambre** (valeur de repli).

Règles complémentaires :

* Le prix est déterminé **nuit par nuit** : un séjour peut avoir des prix différents selon les nuits (semaine / week-end, changement de saison).
* Supplément par adulte au-delà de l'occupation de base et par enfant (paramétrable par tarif).
* La chambre elle-même ne porte pas de prix (suppression du champ « prix » de la chambre en v1). Un supplément optionnel par chambre (vue, étage) peut être ajouté en MVP 3.
* Une modification de dates **recalcule les nouvelles nuits** ; les nuits conservées gardent leur prix figé, sauf demande explicite de recalcul.
* Promotions (MVP 2) : appliquées après résolution du prix, sous forme de remise (§8.4).

## 8.3 Taxes

Chaque taxe est paramétrée par hôtel avec une période de validité (historique des taux) :

| Type de taxe | Calcul | Exemple |
|---|---|---|
| `PERCENTAGE` | Pourcentage de la base HT | TVA (taux paramétrable, ex. 18 %) |
| `FIXED_PER_PERSON_NIGHT` | Montant fixe × personnes assujetties × nuits | Taxe de séjour / taxe de promotion touristique |
| `FIXED_PER_UNIT` | Montant fixe par unité vendue | Taxe spécifique sur un produit |

Règles :

* Chaque taxe s'applique à des **catégories de charge** : hébergement, restauration, boissons, services, autres.
* Exonérations paramétrables pour la taxe de séjour : enfants sous un âge donné, résidents, missions officielles.
* La taxe de séjour **n'est pas soumise à la TVA** (paramétrable).
* Paramètre hôtel `prices_include_tax` (H8) :
  * **TTC** : HT = TTC ÷ (1 + taux) ; TVA = TTC − HT.
  * **HT** : TVA = HT × taux ; TTC = HT + TVA.
* La taxe est calculée **par ligne de charge**, arrondie à la ligne, puis sommée. Le taux et le montant de chaque taxe sont **figés sur la charge** au moment de son enregistrement.

## 8.4 Remises

* Remise sur une ligne : en pourcentage ou en montant fixe, appliquée **avant taxes** (sur le HT ou le TTC selon le mode de prix).
* Remise globale sur un folio : répartie au prorata des lignes concernées.
* **Plafond de remise par rôle** (paramétrable, ex. REC 10 %, DIR 30 %). Au-delà : permission `DISCOUNT_ABOVE_LIMIT` requise (validation par un utilisateur habilité, tracée avec son identité).
* Motif obligatoire pour toute remise. Toute remise est auditée.

## 8.5 Arrondis et devises

* Chaque devise a un nombre de décimales (ISO 4217) : XOF = 0, EUR = 2, USD = 2.
* Mode d'arrondi : **au plus proche, demi vers le haut**.
* Arrondi appliqué à chaque ligne (HT, chaque taxe, TTC), jamais seulement au total.
* Stockage en `NUMERIC(18,2)` ; aucun calcul monétaire en virgule flottante (`BigDecimal` côté backend avec `RoundingMode.HALF_UP`, jamais `double`).

## 8.6 Solde

* **Solde du folio** = Σ charges TTC (y compris taxes forfaitaires) − Σ paiements nets (paiements − remboursements).
* **Solde de la réservation** = Σ soldes de ses folios.
* Un solde négatif signifie un trop-perçu à rembourser ou à transférer.

## 8.7 Exemple de référence (test d'acceptation)

Hypothèses : prix TTC, TVA 18 %, taxe de séjour 1 000 XOF par adulte et par nuit (montant fictif pour l'exemple), XOF sans décimale.

Séjour : 2 adultes, chambre Deluxe à 50 000 XOF TTC/nuit, 3 nuits, remise de 10 % sur l'hébergement, un minibar à 5 000 XOF TTC, acompte de 50 000 XOF.

| Ligne | TTC | HT | TVA |
|---|---|---|---|
| Nuit 1 : 50 000 − 10 % = 45 000 | 45 000 | 38 136 | 6 864 |
| Nuit 2 | 45 000 | 38 136 | 6 864 |
| Nuit 3 | 45 000 | 38 136 | 6 864 |
| Minibar | 5 000 | 4 237 | 763 |
| Taxe de séjour (2 × 3 × 1 000) | 6 000 | — | — |
| **Total** | **146 000** | **118 645** | **21 355** |

Paiements : 50 000 → **Solde = 96 000 XOF**.

---

# 9. CHAMBRES

## 9.1 Types de chambres

Exemples : Standard, Supérieure, Deluxe, Suite, Suite familiale, VIP.

| Attribut | Description |
|---|---|
| Code, nom, description | Code court unique par hôtel (ex. `DLX`) |
| Occupation de base | Nombre de personnes incluses dans le prix |
| Capacité maximale | Total, adultes max, enfants max |
| Prix de base | Valeur de repli de la tarification (§8.2) |
| Équipements | Liste paramétrable (climatisation, TV, minibar, coffre…) |
| Photos | Stockage objet |
| Ordre d'affichage, actif | |

## 9.2 Chambres

| Attribut | Description |
|---|---|
| Numéro | Unique par hôtel |
| Étage, bâtiment / aile | |
| Type de chambre | Obligatoire |
| Caractéristiques | Vue, accessibilité PMR, communicante avec… |
| Statuts | Voir §9.3 |
| Observations | Texte libre |
| Active | Une chambre inactive n'apparaît plus à la vente |

## 9.3 Statuts de chambre — trois dimensions

Les statuts sont séparés en **trois dimensions indépendantes**. La disponibilité future d'une chambre dépend des **réservations et blocages datés**, jamais d'un statut.

| Dimension | Valeurs | Mise à jour |
|---|---|---|
| **Occupation** | `VACANT`, `OCCUPIED` | Automatique : check-in → `OCCUPIED`, check-out → `VACANT` |
| **Ménage** | `DIRTY`, `CLEANING`, `CLEAN`, `INSPECTED` | Housekeeping ; automatique au check-out → `DIRTY` |
| **Disponibilité technique** | `IN_SERVICE`, `MAINTENANCE`, `OUT_OF_ORDER` | Déduite des blocages actifs à la date d'exploitation |

**Statut synthétique** affiché dans l'interface (calculé) : ex. « Libre – Propre », « Libre – Sale », « Occupée – Sale » (recouche), « Occupée – Propre », « Maintenance », « Hors service ». Les arrivées et départs du jour sont signalés par une icône.

**Cycle du ménage** : `DIRTY → CLEANING → CLEAN → INSPECTED`. L'inspection est optionnelle (paramètre `require_inspection`) ; si elle est désactivée, `CLEAN` suffit pour le check-in.

**Historisation** : tout changement de statut (dimension, ancienne valeur, nouvelle valeur, utilisateur, horodatage, motif) est enregistré dans `RoomStatusHistory`.

## 9.4 Blocages de chambre

| Attribut | Description |
|---|---|
| Chambre, date de début, date de fin | Période obligatoire (une date de fin estimée est exigée) |
| Type | `MAINTENANCE` (temporaire), `OUT_OF_ORDER` (hors service, retirée de l'inventaire), `HOUSE_USE` (usage interne) |
| Motif, ticket de maintenance lié | |
| Créé par, levé par | |

Règles :

* Un blocage ne peut pas chevaucher une ligne de réservation déjà attribuée à cette chambre. Le système liste les réservations en conflit et impose leur réaffectation avant validation.
* Les chambres `OUT_OF_ORDER` sont **exclues de la capacité** pour le calcul du taux d'occupation ; les chambres `MAINTENANCE` et `HOUSE_USE` y restent incluses (paramétrable).

---

# 10. CLIENTS

## 10.1 Informations

| Groupe | Champs |
|---|---|
| Identité | Civilité, nom, prénom, date et lieu de naissance, nationalité, sexe, profession |
| Contact | Téléphone (format international), email, adresse, pays de résidence |
| Pièce d'identité | Type (CNI, passeport, carte CEDEAO, titre de séjour…), numéro, pays de délivrance, date de délivrance, date d'expiration, scan (optionnel, chiffré) |
| Rattachement | Entreprise (optionnelle) |
| Préférences | Étage, type de lit, régime alimentaire, notes |
| Marketing | Consentement communication (oui/non, date) |
| Statut | VIP, liste noire (avec motif, permission dédiée) |

## 10.2 Règles

* **Détection des doublons** à la création : recherche sur téléphone, email, numéro de pièce, et nom + date de naissance. Avertissement non bloquant.
* **Fusion** de deux fiches (MVP 2, permission `CUSTOMER_MERGE`) : l'historique est rattaché à la fiche conservée, l'opération est auditée.
* Recherche rapide par nom (insensible aux accents et à la casse, recherche approximative), téléphone, email, numéro de pièce, numéro de réservation.
* La pièce d'identité est **obligatoire au check-in** pour tout occupant adulte (paramétrable).
* Une pièce expirée génère un avertissement au check-in.

## 10.3 Historique client

La fiche client présente : réservations, séjours, factures, paiements, consommations, réclamations (MVP 2), avec les totaux (nombre de séjours, nuitées, chiffre d'affaires).

---

# 11. ENTREPRISES

## 11.1 Informations

Raison sociale, sigle, RCCM, identifiant fiscal (libellé paramétrable par pays : NINEA, IFU, NCC…), adresse, téléphone, email, contacts (nom, fonction, téléphone, email), adresse de facturation, conditions de paiement (comptant, 30 jours…), plafond de crédit, tarifs négociés, statut (actif / suspendu).

## 11.2 Règles

* Une entreprise peut être associée à plusieurs clients et plusieurs réservations.
* **MVP 1** : fiche entreprise, rattachement à la réservation, facture établie au nom de l'entreprise.
* **MVP 3** :
  * tarifs négociés (§8.2) ;
  * facturation différée sur compte entreprise (**comptes débiteurs**) ;
  * contrôle du plafond de crédit : encours + nouveau montant > plafond → blocage, sauf permission `COMPANY_CREDIT_OVERRIDE` ;
  * relevés de compte et balance âgée (0–30, 31–60, 61–90, plus de 90 jours).

---

# 12. RÉSERVATIONS

## 12.1 Structure

**Réservation (en-tête)**

| Champ | Description |
|---|---|
| Numéro | `RES-{CODE_HOTEL}-{AAAA}-{séquence}` (trous autorisés) |
| Client titulaire | Obligatoire |
| Entreprise | Optionnelle |
| Canal | Paramétrable : réception, téléphone, email, site web, Booking, Expedia, agence, entreprise, walk-in, autre OTA |
| Référence externe | Numéro de réservation OTA / agence |
| Garantie | Aucune, acompte, carte, entreprise |
| Date d'expiration de l'option | Obligatoire si statut `PENDING` |
| Politique d'annulation | Copiée du plan tarifaire au moment de la réservation |
| Commentaires, demandes particulières | |
| Totaux calculés | Montant total, remises, taxes, acompte versé, solde |

**Ligne de réservation** (une par chambre)

| Champ | Description |
|---|---|
| Type de chambre | Obligatoire |
| Chambre | Optionnelle à la réservation, **obligatoire au check-in** |
| Date d'arrivée, date de départ | |
| Adultes, enfants (avec âges) | Contrôle de la capacité du type |
| Plan tarifaire | |
| Prix par nuit | Figé (§8.2) |
| Remise | §8.4 |
| Occupants | Liste des clients occupants (au moins le titulaire de la ligne au check-in) |
| Statut | §12.2 |

Une réservation de **groupe** est une réservation avec plusieurs lignes. Au MVP 1, chaque ligne a son propre folio ; un folio maître de groupe et des règles de routage des charges sont prévus en MVP 3.

## 12.2 Statuts d'une ligne

| Statut | Signification | Bloque l'inventaire |
|---|---|---|
| `PENDING` | Option non garantie | Oui, jusqu'à la date d'expiration |
| `CONFIRMED` | Réservation confirmée | Oui |
| `CHECKED_IN` | Client en maison | Oui |
| `CHECKED_OUT` | Séjour terminé | Non (libère les nuits restantes) |
| `CANCELLED` | Annulée | Non |
| `NO_SHOW` | Client non présenté | Non |

**Transitions autorisées**

| De | Vers | Déclencheur | Permission |
|---|---|---|---|
| `PENDING` | `CONFIRMED` | Confirmation (garantie reçue ou manuelle) | `RESERVATION_UPDATE` |
| `PENDING` | `CANCELLED` | Annulation manuelle ou expiration de l'option (automatique) | `RESERVATION_CANCEL` / système |
| `CONFIRMED` | `CHECKED_IN` | Check-in | `CHECKIN_CREATE` |
| `CONFIRMED` | `CANCELLED` | Annulation | `RESERVATION_CANCEL` |
| `CONFIRMED` | `NO_SHOW` | Clôture journalière ou manuel | `RESERVATION_NO_SHOW` / système |
| `CHECKED_IN` | `CHECKED_OUT` | Check-out | `CHECKOUT_CREATE` |
| `CANCELLED` / `NO_SHOW` | `CONFIRMED` | Réactivation (si disponibilité) | `RESERVATION_REINSTATE` |

Toute autre transition est refusée (`409 RESERVATION_INVALID_STATUS`).

**Statut de l'en-tête** (calculé) : `PENDING` si toutes les lignes actives sont en option, `CONFIRMED`, `IN_HOUSE` si au moins une ligne est en maison, `COMPLETED` si toutes les lignes sont terminées, `CANCELLED` / `NO_SHOW` si toutes les lignes le sont.

## 12.3 Création — workflow

```text
Recherche de disponibilité (dates, nombre de personnes)
 ↓
Choix du ou des types de chambre (et éventuellement des chambres)
 ↓
Sélection ou création du client (contrôle des doublons)
 ↓
Calcul des prix par nuit (§8.2) et des totaux
 ↓
Acompte éventuel (paiement sur le folio de la réservation)
 ↓
Enregistrement : PENDING (option) ou CONFIRMED
 ↓
Email de confirmation (si email renseigné)
```

L'enregistrement est **transactionnel** et **idempotent** (en-tête `Idempotency-Key`).

## 12.4 Modification

* Modification des dates, du type, de la chambre, du nombre de personnes, du tarif : nouvelle vérification de disponibilité et recalcul (§8.2).
* Écran de confirmation montrant l'écart de prix avant validation.
* Une ligne `CHECKED_IN` ne peut modifier que sa date de départ (prolongation / départ anticipé, §14).
* Toute modification est auditée (anciennes et nouvelles valeurs).

## 12.5 Annulation

* **Politique d'annulation** (par hôtel, surchargée par plan tarifaire) :
  * annulation gratuite jusqu'à N heures avant la date d'arrivée (heure de check-in) ;
  * au-delà : pénalité = X nuits ou Y % du séjour ;
  * no-show : pénalité = première nuit par défaut.
* À l'annulation, le système calcule la pénalité et la porte au folio de la réservation. L'utilisateur peut y renoncer (motif obligatoire, permission `DISCOUNT_ABOVE_LIMIT`).
* L'acompte couvre la pénalité ; le reliquat éventuel est remboursé (§20.4) ou conservé en avoir client (MVP 3).
* Motif d'annulation obligatoire (liste paramétrable).

## 12.6 Options expirées et no-show

* Les options (`PENDING`) dont la date d'expiration est dépassée sont annulées automatiquement (tâche planifiée + contrôle à la clôture journalière).
* Les lignes `CONFIRMED` dont la date d'arrivée est la date d'exploitation et non arrivées sont traitées à la clôture journalière (§17) : passage en `NO_SHOW` automatique ou proposé (paramètre).

---

# 13. DISPONIBILITÉ — RÈGLE CRITIQUE

## 13.1 Définition

Une chambre est disponible sur la période [arrivée, départ[ si :

1. elle est active ;
2. aucune ligne de réservation au statut `PENDING` (non expirée), `CONFIRMED` ou `CHECKED_IN` ne lui est attribuée avec une période qui chevauche ;
3. aucun blocage ne chevauche la période ;
4. sa capacité est compatible avec les occupants demandés.

Le statut ménage n'intervient pas dans la disponibilité future ; il est contrôlé au check-in (§14).

**Disponibilité d'un type de chambre pour une nuit N** = chambres actives du type − chambres bloquées en N − lignes attribuées à une chambre de ce type en N − lignes non attribuées de ce type en N.

Un type est disponible sur une période si sa disponibilité est ≥ 1 **pour chaque nuit**.

## 13.2 Garanties techniques

* La vérification est effectuée **exclusivement côté serveur**, dans la transaction d'enregistrement.
* **Chambre attribuée** : contrainte d'exclusion PostgreSQL :

  ```sql
  ALTER TABLE reservation_room ADD CONSTRAINT no_room_overlap
  EXCLUDE USING gist (
    room_id WITH =,
    daterange(arrival_date, departure_date, '[)') WITH &&
  ) WHERE (room_id IS NOT NULL AND status IN ('PENDING','CONFIRMED','CHECKED_IN'));
  ```

  Une contrainte équivalente s'applique aux blocages. Une violation est traduite en `409 ROOM_NOT_AVAILABLE`.
* **Ligne par type (sans chambre)** : verrou transactionnel par (hôtel, type de chambre) (`pg_advisory_xact_lock`) pendant le contrôle et l'insertion, pour sérialiser les réservations concurrentes sur un même type.
* Le check-in sur une chambre en **départ** du jour n'est possible qu'après le check-out de l'occupant précédent.

## 13.3 Recherche de disponibilité

`GET /api/v1/hotels/{hotelId}/availability?arrival=…&departure=…&adults=…&children=…` renvoie par type : nombre de chambres disponibles, prix par nuit et total selon les plans tarifaires applicables, et la liste des chambres libres (option).

---

# 14. PLANNING

## 14.1 Affichage

* Grille : chambres en lignes (regroupées par type et étage), jours en colonnes (7, 14 ou 30 jours).
* Barres de réservation colorées par statut ; blocages hachurés ; arrivées et départs du jour mis en évidence.
* Ligne « non attribuées » par type, affichant les lignes sans chambre.
* Compteurs de disponibilité par type et par jour en pied de grille.
* Filtres : type, étage, statut ; recherche par nom ou numéro de réservation.
* Info-bulle : client, dates, nombre de personnes, solde, canal.

## 14.2 Actions

| Action | Règles |
|---|---|
| Déplacer une réservation (glisser-déposer) | Même durée, nouvelles dates ; recalcul des prix proposé ; vérification serveur |
| Changer de chambre | Avant arrivée : libre. En séjour : voir ci-dessous |
| Prolonger (étirer la barre) | Vérification de disponibilité ; nouvelles nuits au tarif applicable |
| Réduire | Avant arrivée : modification simple. En séjour : départ anticipé |
| Attribuer une chambre | Depuis la ligne « non attribuées » |
| Créer une réservation | Sélection d'une plage vide |

Chaque action affiche une **confirmation** récapitulant l'impact (dates, prix), est **validée par le serveur** (le planning est rafraîchi en cas de conflit `409`) et est **auditée**.

## 14.3 Changement de chambre en cours de séjour

* Crée un nouveau segment de séjour (`StayRoomSegment`) à la date d'exploitation.
* Ancienne chambre : occupation `VACANT`, ménage `DIRTY`. Nouvelle chambre : `OCCUPIED`.
* Le prix des nuits restantes est **conservé par défaut** (surclassement gratuit) ; recalcul possible avec `RATE_OVERRIDE`.
* Motif obligatoire (liste paramétrable : panne, demande client, surclassement…).

---

# 15. SÉJOUR ET FOLIO

## 15.1 Séjour

Créé au check-in à partir d'une ligne de réservation. Contient : la ligne de réservation, les segments de chambre, les occupants, les dates réelles d'arrivée et de départ (horodatées), le statut (`IN_HOUSE`, `CHECKED_OUT`) et le ou les folios.

## 15.2 Folio

| Attribut | Description |
|---|---|
| Numéro | Unique par hôtel |
| Type | `GUEST` (client), `COMPANY` (entreprise), `GROUP` (maître de groupe, MVP 3), `PASSANT` (vente hors hébergement, MVP 3) |
| Rattachement | Réservation (obligatoire pour les types GUEST, COMPANY, GROUP), séjour, entreprise |
| Titulaire | Client ou entreprise facturé |
| Statut | `OPEN`, `CLOSED` (après facturation et solde nul) |
| Solde | Calculé (§8.6) |

* Un folio `GUEST` est créé **à la création de chaque ligne de réservation** ; il reçoit les acomptes avant l'arrivée puis est rattaché au séjour au check-in.
* Plusieurs folios peuvent coexister pour un même séjour (ex. hébergement payé par l'entreprise, extras payés par le client). Au MVP 1 : création manuelle d'un second folio et transfert manuel de charges (`FOLIO_TRANSFER`).

## 15.3 Charges

| Attribut | Description |
|---|---|
| Date d'exploitation | Date à laquelle la charge est comptabilisée |
| Catégorie | `ACCOMMODATION`, `FOOD`, `BEVERAGE`, `SERVICE`, `TAX`, `PENALTY`, `ADJUSTMENT` |
| Source | Nuitée, service, commande restaurant, pénalité, manuel |
| Libellé, quantité, prix unitaire, remise | |
| HT, détail des taxes, TTC | Figés (§8.3) |
| Statut | `POSTED`, `TRANSFERRED`, `CORRECTED` |

Règles :

* Une charge n'est jamais supprimée ni modifiée. Une erreur est corrigée par une **charge inverse** (montant négatif, référence à la charge d'origine, motif obligatoire, permission `FOLIO_CHARGE_CORRECT`).
* Aucune charge ne peut être enregistrée sur une date d'exploitation clôturée.
* Une charge déjà facturée ne peut plus être transférée ni corrigée (correction par avoir).

## 15.4 Pendant le séjour

Le système permet : ajout de services (§22), consommations restaurant / minibar / blanchisserie / transport / spa / parking, changement de chambre (§14.3), prolongation, départ anticipé, modification des occupants, ajout de paiements, impression d'un relevé de folio provisoire.

* **Prolongation** : vérification de disponibilité de la chambre ; sinon proposition de changement de chambre.
* **Départ anticipé** : la date de départ est ramenée à la date d'exploitation ; les nuits non consommées sont libérées ; pénalité éventuelle selon la politique.

---

# 16. CHECK-IN

## 16.1 Préconditions

* Ligne au statut `CONFIRMED` (ou `PENDING` avec confirmation simultanée).
* Date d'arrivée = date d'exploitation. Une arrivée anticipée d'un jour impose de modifier les dates de la réservation (avec recalcul).
* Chambre attribuée, disponible, en état `IN_SERVICE`.
* Statut ménage `CLEAN` ou `INSPECTED` selon le paramètre. Sinon blocage, sauf permission `CHECKIN_OVERRIDE_HOUSEKEEPING` (motif tracé).

## 16.2 Workflow

1. Rechercher la réservation (nom, numéro, téléphone) ou la liste des arrivées du jour.
2. Identifier le client titulaire et compléter sa fiche.
3. Enregistrer les occupants adultes et vérifier leurs pièces d'identité (obligatoires selon le paramètre).
4. Attribuer ou confirmer la chambre.
5. Appliquer la politique de garantie de l'hôtel (paramètre : aucune, première nuit, totalité du séjour, montant forfaitaire de caution).
6. Enregistrer le paiement éventuel (§20).
7. Valider le check-in : ligne → `CHECKED_IN`, création du séjour, rattachement du folio, chambre → `OCCUPIED`.
8. Générer la **fiche d'enregistrement / fiche de police** (PDF, une par occupant adulte, à signer).

Les étapes 7 et 8 sont exécutées dans une transaction unique et idempotente.

## 16.3 Walk-in (sans réservation)

Création d'une réservation au canal `WALK_IN` et check-in immédiat, dans un seul écran et une seule transaction. Paramètre hôtel : paiement de la première nuit obligatoire ou non pour un walk-in.

---

# 17. CLÔTURE JOURNALIÈRE (NIGHT AUDIT)

## 17.1 Principe

L'hôtel fonctionne sur une **date d'exploitation** stockée par hôtel. Toutes les charges, paiements et mouvements de caisse portent cette date. La clôture journalière clôture la date en cours et passe à la suivante. Une date clôturée est **verrouillée** : toute correction s'effectue sur la date suivante.

## 17.2 Déclenchement

* MVP 1 : déclenchement **manuel** par un utilisateur ayant `NIGHT_AUDIT_RUN`, généralement entre minuit et 6 h.
* MVP 2 : déclenchement automatique optionnel à une heure paramétrée.
* Avertissement si la clôture n'a pas été faite à l'heure paramétrée (notification au DIR).

## 17.3 Étapes

| # | Étape | Comportement |
|---|---|---|
| 1 | Contrôle des départs non effectués | **Bloquant** : chaque ligne dont le départ est la date d'exploitation doit être sortie ou prolongée |
| 2 | Contrôle des arrivées non effectuées | Liste présentée : passer en no-show (avec pénalité selon la politique), décaler, ou laisser (paramètre de traitement automatique) |
| 3 | Options expirées | Annulation automatique |
| 4 | Sessions de caisse ouvertes | Avertissement (ou blocage, selon le paramètre) |
| 5 | Enregistrement des nuitées | Pour chaque séjour en maison : charge d'hébergement au prix figé de la nuit + taxes, dont la taxe de séjour |
| 6 | Statuts ménage | Chambres occupées → `DIRTY` (recouche), selon le paramètre |
| 7 | Rapports | Génération et archivage PDF : main courante, rapport de caisse, occupation, arrivées/départs du lendemain, **rapport de secours** (clients en maison, chambres, soldes) |
| 8 | Passage de date | Date d'exploitation = date + 1 ; la date précédente est verrouillée |

## 17.4 Robustesse

* Chaque étape est **journalisée** (début, fin, résultat) et **rejouable** sans doublon : une nuitée n'est jamais enregistrée deux fois pour le même séjour et la même nuit (contrainte d'unicité `stay_id + night_date` sur les charges d'hébergement).
* En cas d'échec, la clôture reprend à l'étape en erreur.
* Un seul traitement de clôture à la fois par hôtel (verrou).

---

# 18. CHECK-OUT

## 18.1 Workflow

1. Rechercher le séjour (liste des départs du jour, nom, chambre).
2. Si départ anticipé : confirmation et mise à jour de la date de départ (§15.4).
3. Vérifier que toutes les nuitées dues sont enregistrées (le cas du day use est enregistré au check-out).
4. Enregistrer les charges en attente (minibar, services de dernière minute).
5. Appliquer les remises éventuelles (§8.4).
6. Afficher le folio : détail des charges, taxes, total HT, TVA, total TTC, paiements, **solde**.
7. Encaisser le solde (§20), ou le transférer sur le compte de l'entreprise (MVP 3, `CHECKOUT_WITH_BALANCE`).
8. Émettre la facture (§19) à partir du folio ; plusieurs factures si plusieurs folios.
9. Clôturer le séjour : ligne → `CHECKED_OUT`, séjour → `CHECKED_OUT`, folio → `CLOSED`.
10. Chambre → occupation `VACANT`, ménage `DIRTY` ; création d'une tâche de nettoyage de départ (MVP 2).
11. Proposer l'envoi de la facture par email.

## 18.2 Règles

* Check-out impossible avec un solde non nul, sauf transfert vers un compte entreprise ou permission `CHECKOUT_WITH_BALANCE` (impayé tracé, apparaît dans les impayés).
* Un trop-perçu doit être remboursé ou transféré avant la clôture.
* Les étapes 8 à 10 sont exécutées dans une transaction unique et idempotente.

---

# 19. FACTURATION

## 19.1 Types de documents

| Type | Préfixe | Numérotation légale | Effet |
|---|---|---|---|
| Facture | `FAC` | Oui, continue | Document comptable |
| Facture pro forma | `PRO` | Non | Devis, sans valeur comptable |
| Avoir | `AV` | Oui, continue | Annule tout ou partie d'une facture |
| Reçu | `REC` | Oui, continue | Justificatif d'un paiement |

## 19.2 Numérotation

* Format : `{PRÉFIXE}-{CODE_HOTEL}-{AAAA}-{séquence sur 6 chiffres}`, ex. `FAC-DKR01-2026-000123`.
* Séquence **par hôtel, par type et par année**, **continue et sans trou**.
* Le numéro est attribué **au moment de l'émission**, dans la même transaction, via une table de compteurs verrouillée (`SELECT … FOR UPDATE`), jamais via une séquence PostgreSQL (qui peut laisser des trous).

## 19.3 Cycle de vie

| Statut | Description |
|---|---|
| `DRAFT` | Brouillon / pro forma, modifiable, sans numéro légal |
| `ISSUED` | Émise : numérotée, **immuable** |
| `PARTIALLY_PAID` / `PAID` | Calculés à partir des paiements (factures entreprise) |
| `CANCELLED` | Annulée **uniquement par un avoir total** |

* Une facture émise ne peut être ni modifiée ni supprimée. Toute correction passe par un **avoir** (permission `CREDIT_NOTE_ISSUE`, motif obligatoire, référence à la facture d'origine), puis éventuellement une nouvelle facture.
* Le PDF émis est **archivé** (stockage objet + empreinte SHA-256 en base) ; une réimpression produit le même document marqué « Duplicata ».

## 19.4 Contenu d'une facture

* **Hôtel** : raison sociale, nom commercial, adresse, téléphone, email, RCCM, identifiant fiscal, logo.
* **Client** : nom, adresse ; **entreprise** le cas échéant : raison sociale, adresse, identifiant fiscal.
* Numéro, date d'émission, date d'exploitation, référence de réservation, chambre, dates du séjour.
* Lignes : libellé, quantité, prix unitaire, remise, montant HT, taux de taxe.
* Récapitulatif par taux de TVA : base HT, montant de TVA.
* Taxes forfaitaires (taxe de séjour) sur une ligne distincte.
* Total HT, total TVA, total des autres taxes, **total TTC**.
* Paiements reçus (date, mode, montant), **solde**.
* Montant TTC en toutes lettres (option, usage courant).
* Mentions de pied de page paramétrables (conditions, coordonnées bancaires).

## 19.5 Diffusion

PDF, impression, téléchargement, envoi par email (MVP 1, via le service d'email transactionnel).

---

# 20. PAIEMENTS

## 20.1 Moyens de paiement

Paramétrables par hôtel :

| Attribut | Description |
|---|---|
| Code, libellé | ex. `CASH`, `CARD`, `TRANSFER`, `CHEQUE`, `OM` (Orange Money), `WAVE`, `MTN_MOMO`, `MOOV` |
| Type | `CASH`, `CARD`, `TRANSFER`, `CHEQUE`, `MOBILE_MONEY`, `OTHER` |
| Opérateur | Pour les intégrations futures (phase 4) |
| Passe par la caisse | Oui pour `CASH` (obligatoire) ; paramétrable pour les autres |
| Référence obligatoire | ex. numéro de chèque, identifiant de transaction Mobile Money |
| Actif | |

Au MVP 1, **tous les paiements sont saisis manuellement** (aucune intégration d'opérateur). La couche d'abstraction des fournisseurs de paiement (`PaymentProvider`) est définie dès le MVP 1 avec une implémentation « manuelle ».

## 20.2 Paiement

| Attribut | Description |
|---|---|
| Référence interne | `PAY-{CODE_HOTEL}-{AAAA}-{séquence}`, unique |
| Référence externe | Numéro de chèque, transaction opérateur ; unique par opérateur |
| Contexte financier | **Exactement un** : folio ou facture (contrainte `CHECK`) |
| Moyen de paiement, montant, devise | |
| Montant converti en devise de l'hôtel, taux de change appliqué | Si devise étrangère (MVP 2) |
| Session de caisse | Obligatoire si le moyen passe par la caisse |
| Date d'exploitation, horodatage, utilisateur | |
| Statut | `PENDING`, `SUCCESS`, `FAILED`, `CANCELLED`, `PARTIALLY_REFUNDED`, `REFUNDED` |
| Clé d'idempotence | §32.4 |

Un reçu (`REC`) est émis pour chaque paiement réussi.

## 20.3 Annulation d'un paiement

Possible uniquement **le même jour d'exploitation** et tant que la session de caisse est ouverte (permission `PAYMENT_CANCEL`, motif obligatoire). Sinon : remboursement.

## 20.4 Remboursement

* Enregistré comme un **paiement de type remboursement** référençant **obligatoirement** le paiement d'origine.
* Montant ≤ montant restant remboursable du paiement d'origine.
* Permission `PAYMENT_REFUND`, motif obligatoire, audité.
* Remboursement en espèces : sortie de caisse dans la session ouverte de l'utilisateur.
* Met à jour le statut du paiement d'origine (`PARTIALLY_REFUNDED` / `REFUNDED`).

## 20.5 Devises (MVP 2)

* Devise principale par hôtel (XOF par défaut).
* Acceptation de paiements en devises étrangères (EUR, USD) avec table de taux de change datés ; le taux utilisé est figé sur le paiement.
* La facturation reste dans la devise de l'hôtel.

---

# 21. CAISSE

## 21.1 Concepts

* **Caisse** : point d'encaissement physique (Réception, Restaurant, Bar).
* **Session de caisse** : ouverture → mouvements → clôture, par un utilisateur, sur une caisse.

## 21.2 Règles

* Une caisse a **au plus une session ouverte** ; un utilisateur a **au plus une session ouverte**.
* **Tout paiement en espèces exige une session ouverte** de l'utilisateur. Les autres moyens de paiement sont rattachés à la session si elle existe (pour le rapport), sans l'exiger (paramétrable).
* Chaque mouvement de caisse est rattaché à une session ouverte.

## 21.3 Ouverture

Caisse, utilisateur, date d'exploitation, horodatage, **fond de caisse initial** (pré-rempli avec le montant laissé à la clôture précédente, écart signalé).

## 21.4 Mouvements

| Type | Origine |
|---|---|
| `PAYMENT_IN` | Encaissement d'un paiement (automatique) |
| `REFUND_OUT` | Remboursement en espèces (automatique) |
| `EXPENSE_OUT` | Dépense payée en espèces (MVP 2) |
| `BANK_DEPOSIT` | Remise en banque / transfert vers le coffre |
| `CASH_IN` / `CASH_OUT` | Apport ou retrait divers, motif obligatoire |
| `ADJUSTMENT` | Correction, permission `CASH_ADJUSTMENT`, motif obligatoire |

## 21.5 Clôture

* **Solde théorique espèces** = fond initial + entrées espèces − sorties espèces.
* Saisie du **solde réel compté** (comptage par coupure en option).
* **Écart** = réel − théorique ; **justification obligatoire** si l'écart est non nul.
* Récapitulatif des encaissements par moyen de paiement (espèces, carte, Mobile Money…) à rapprocher des relevés.
* Une session clôturée est **immuable** et ne peut pas être rouverte. Une erreur est corrigée par un ajustement dans une session ultérieure.
* La clôture est auditée et produit un **rapport de caisse PDF**.

---

# 22. SERVICES ADDITIONNELS

## 22.1 Catalogue

Petit-déjeuner, minibar, blanchisserie, spa, massage, parking, transport / navette, room service, excursions, early check-in, late check-out, lit supplémentaire…

| Attribut | Description |
|---|---|
| Code, nom, catégorie | Catégorie paramétrable |
| Catégorie de charge | Détermine les taxes applicables (§8.3) |
| Prix, unité | Par unité, par personne, par nuit, par personne et par nuit |
| Disponibilité | Horaires, jours (informatif) |
| Statut | Actif / inactif |

## 22.2 Utilisation

* **MVP 1** : ajout manuel d'un service au folio d'un séjour (quantité, prix, remise).
* **MVP 2** : services inclus dans un plan tarifaire (ex. « Chambre + petit-déjeuner ») portés automatiquement par la clôture journalière, avec ventilation du chiffre d'affaires par catégorie.

---

# 23. RESTAURANT / BAR (MVP 3)

## 23.1 Gestion

Points de vente (restaurant, bar, room service), catégories de produits, produits (prix, catégorie de charge, article de stock lié et quantité consommée), tables, serveurs, commandes, additions.

## 23.2 Commande

| Champ | Description |
|---|---|
| Point de vente, table, serveur, nombre de couverts | |
| Client / séjour | Optionnels |
| Lignes | Produit, quantité, prix, remise, taxes, statut |
| Total | |
| Statut | `OPEN`, `SENT` (envoyée en cuisine/bar), `SERVED`, `BILLED`, `PAID`, `TRANSFERRED_TO_ROOM`, `CANCELLED` |

Règles :

* Annulation d'une ligne déjà envoyée en cuisine : permission `POS_ORDER_VOID` et motif.
* Paiement direct (avec session de caisse du point de vente) ou **transfert en chambre**.
* La sortie de stock est générée à l'envoi de la commande (paramètre).

## 23.3 Transfert en chambre

* Contrôles : séjour en maison, numéro de chambre et nom du client concordants, signature du client sur l'addition (papier ou tablette).
* Le montant est porté au folio du séjour sous forme de charges détaillées par catégorie (nourriture, boissons) avec la référence de la commande.
* Plafond de transfert paramétrable (ex. selon les garanties déposées).

## 23.4 Clients non hébergés

Vente directe avec ticket ou facture (folio `PASSANT`).

---

# 24. HOUSEKEEPING

## 24.1 MVP 1

* Tableau des chambres avec statut ménage, occupation, arrivées et départs du jour.
* Mise à jour manuelle du statut ménage (`HK_STATUS_UPDATE`), historisée.
* Passage automatique à `DIRTY` au check-out et (selon le paramètre) à la clôture journalière pour les recouches.

## 24.2 MVP 2 — tâches

| Chambre | Type de tâche | Statut | Agent | Priorité |
|---|---|---|---|---|
| 101 | Départ | À faire | Agent A | Normale |
| 102 | Recouche | En cours | Agent B | Haute (arrivée VIP) |
| 103 | Départ | Terminée — à inspecter | Agent C | — |

* Génération automatique quotidienne : nettoyage de départ, recouche, préparation d'arrivée.
* Priorité automatique : chambre en départ avec arrivée le jour même = haute.
* Le responsable affecte les tâches, modifie les priorités, inspecte (`INSPECTED` ou retour à `DIRTY` avec commentaire).
* L'agent dispose d'une vue tablette/mobile : ses tâches, démarrage (`CLEANING`), fin (`CLEAN`), signalement d'anomalie (création d'un ticket de maintenance, objets trouvés, consommation minibar).

**Cycle** : `Occupée → DIRTY → CLEANING → CLEAN → INSPECTED → disponible pour check-in`.

---

# 25. MAINTENANCE

## 25.1 MVP 1

Blocages manuels de chambre de type `MAINTENANCE` / `OUT_OF_ORDER` (§9.4).

## 25.2 MVP 3 — tickets

| Champ | Description |
|---|---|
| Numéro | `MNT-{CODE_HOTEL}-{AAAA}-{séquence}` |
| Chambre ou zone, équipement | |
| Catégorie de problème | Plomberie, électricité, climatisation, mobilier… |
| Description, photos | |
| Priorité | Basse, normale, haute, urgente |
| Bloquant | Oui / non |
| Demandeur, technicien affecté | |
| Dates | Création, prise en charge, résolution, clôture |
| Statut | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| Résolution, coût | |

Règles :

* Un ticket **bloquant** crée un blocage `MAINTENANCE` de la chambre (date de fin estimée obligatoire) ; les réservations en conflit sont signalées pour réaffectation.
* La résolution lève le blocage et passe la chambre en `DIRTY` (nettoyage après intervention).
* Équipements : inventaire par chambre ou zone, historique des interventions.

---

# 26. STOCKS (MVP 3)

## 26.1 Articles

Nom, catégorie, unité, stock minimum, quantité en stock (calculée), coût moyen pondéré (CMUP), fournisseur principal, actif.

## 26.2 Mouvements

| Type | Effet |
|---|---|
| `IN` | Entrée (réception d'achat) — recalcul du CMUP |
| `OUT` | Sortie (consommation restaurant, minibar, service) |
| `ADJUSTMENT` | Ajustement motivé (casse, perte), permission `STOCK_ADJUST` |
| `INVENTORY` | Écart constaté lors d'un inventaire |

Règles :

* Quantité disponible = Σ mouvements ; une quantité est maintenue en cache sur l'article, mise à jour sous verrou dans la même transaction que le mouvement.
* **Une sortie ne peut pas rendre le stock négatif** (paramètre `allow_negative_stock = false` par défaut).
* Inventaire : figer les quantités théoriques, saisir les quantités comptées, valider → mouvements `INVENTORY`.
* **Alerte** lorsque stock ≤ stock minimum (notification interne).

---

# 27. FOURNISSEURS ET ACHATS (MVP 3)

* Fournisseurs : raison sociale, identifiants légaux, contacts, conditions de paiement, produits fournis.
* Bons de commande : lignes, statut (`DRAFT`, `SENT`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`).
* Réceptions : génèrent des entrées de stock.
* Factures fournisseurs, paiements fournisseurs (espèces via caisse, virement, chèque).
* Historique et solde par fournisseur.

---

# 28. DÉPENSES (MVP 2)

| Champ | Description |
|---|---|
| Catégorie | Paramétrable : alimentation, électricité, eau, internet, maintenance, fournitures, transport, marketing, autres |
| Montant, devise, date | |
| Fournisseur | Optionnel |
| Moyen de paiement | Espèces → mouvement de caisse `EXPENSE_OUT` |
| Justificatif | Fichier joint (photo, PDF) |
| Utilisateur, commentaire | |
| Statut | `DRAFT`, `VALIDATED`, `CANCELLED` |

* Validation par `EXPENSE_VALIDATE` au-delà d'un seuil paramétrable.
* Une dépense validée n'est pas modifiable ; correction par annulation motivée.

---

# 29. TARIFICATION

## 29.1 Plans tarifaires

| Attribut | Description |
|---|---|
| Code, nom | ex. `STD` Standard, `CORP` Corporate, `WE` Week-end, `LS` Long séjour, `BB` Chambre + petit-déjeuner |
| Type | Standard, négocié entreprise, promotionnel |
| Conditions | Nombre de nuits min/max, jours de la semaine, catégorie de client, entreprise(s) |
| Services inclus | ex. petit-déjeuner (MVP 2) |
| Politique d'annulation | |
| Actif, période de validité | |

## 29.2 Prix

Pour chaque (plan tarifaire, type de chambre, période) : prix par nuit, éventuellement différencié par jour de la semaine, supplément adulte et enfant, prix day use.

Les **saisons** (haute, basse) sont des périodes définies dans les prix du plan standard.

## 29.3 Phasage

* **MVP 1** : plan standard avec saisons et différenciation semaine / week-end, prix manuel avec permission.
* **MVP 3** : plans multiples avec conditions, tarifs négociés entreprise, long séjour, catégories de clients.

Tout changement de tarif est audité ; il n'affecte pas les réservations existantes (prix figés).

---

# 30. PROMOTIONS (MVP 2)

Code promotionnel, réduction fixe ou en pourcentage, période de réservation et période de séjour, types de chambres concernés, nombre d'utilisations maximum (global et par client), nombre de nuits minimum, cumul autorisé ou non. Chaque utilisation est enregistrée.

---

# 31. NOTIFICATIONS

## 31.1 Architecture

* Événements métier publiés via l'**outbox** (§5, ADR-09).
* Abstraction `NotificationChannel` : in-app, email, SMS, WhatsApp.
* Modèles de messages paramétrables par hôtel et par langue.

## 31.2 Phasage

| Phase | Contenu |
|---|---|
| MVP 1 | **Email transactionnel** (SMTP) : réinitialisation de mot de passe, confirmation de réservation, envoi de facture |
| MVP 2 | Notifications internes (centre de notifications, badge) : nouvelle réservation, annulation, arrivées imminentes, départs, clôture non faite, écart de caisse, paiement important |
| MVP 3 | Stock faible, ticket de maintenance, plafond de crédit atteint |
| Phase 4 | SMS, WhatsApp |

---

# 32. AUDIT, IDEMPOTENCE ET CONCURRENCE

## 32.1 Audit (MVP 1)

Opérations auditées : création, modification, suppression logique, annulation, changement de statut, remboursement, changement de tarif, prix manuel, remise, modification de réservation, changement de chambre, émission de facture, avoir, paiement, annulation de paiement, mouvement et clôture de caisse, clôture journalière, connexions, gestion des utilisateurs et des rôles, consultation des pièces d'identité.

| Champ | Description |
|---|---|
| Horodatage (UTC) | |
| Organisation, hôtel | |
| Utilisateur, rôle actif | |
| Action | ex. `RESERVATION_UPDATE` |
| Type d'objet, identifiant | |
| Ancienne valeur, nouvelle valeur | JSON (champs modifiés uniquement) |
| Motif | Si exigé |
| Adresse IP, user agent | |
| Résultat | `SUCCESS` / `FAILURE` (avec code d'erreur) |
| traceId | Corrélation avec les logs |

* Table **en ajout seul** : l'utilisateur applicatif de la base n'a pas les droits `UPDATE`/`DELETE` sur cette table.
* Conservation : 10 ans pour les opérations financières, 2 ans pour les autres (paramétrable selon la réglementation).
* Consultation filtrable (utilisateur, période, objet, action) et export, permission `AUDIT_VIEW`.
* Les données personnelles sensibles (numéros de pièce) sont masquées dans les valeurs d'audit.

## 32.2 Transactions

Les opérations suivantes sont atomiques (une transaction base de données) : création / modification de réservation, check-in, check-out, paiement + mouvement de caisse + reçu, émission de facture + numérotation, avoir, clôture de caisse, chaque étape de la clôture journalière, mouvement de stock.

## 32.3 Verrouillage optimiste

Les entités modifiables portent une colonne `version`. Le client renvoie la version lue (`If-Match`) ; une version obsolète produit `409 VERSION_CONFLICT` et l'interface propose de recharger.

## 32.4 Idempotence

* En-tête `Idempotency-Key` (UUID généré par le client) **obligatoire** sur : création de réservation, check-in, check-out, paiement, remboursement, émission de facture, avoir, commande restaurant, mouvement de caisse.
* Table `idempotency_key` : (hôtel, utilisateur, clé, empreinte de la requête, réponse, date). Conservation 24 heures.
* Même clé et même requête → la réponse initiale est renvoyée sans nouvelle opération.
* Même clé et requête différente → `422 IDEMPOTENCY_KEY_REUSED`.
* Le frontend désactive le bouton de validation pendant l'envoi et réutilise la même clé lors d'un nouvel essai.

---

# 33. MODÈLE DE DONNÉES

## 33.1 Conventions

* Identifiants : UUID (v7, triables chronologiquement).
* Colonnes communes : `id`, `organization_id`, `hotel_id` (sauf entités d'organisation), `created_at`, `created_by`, `updated_at`, `updated_by`, `version`, `deleted_at` (référentiels uniquement).
* Horodatages en `timestamptz` (UTC) ; dates d'exploitation et de séjour en `date` (fuseau horaire de l'hôtel).
* Montants en `NUMERIC(18,2)` accompagnés de `currency_code CHAR(3)`.
* Énumérations : type texte + contrainte `CHECK` (évolutif).
* Nommage : tables et colonnes en `snake_case` anglais.
* Schéma géré exclusivement par migrations **Flyway** (`ddl-auto` désactivé).

## 33.2 Entités par domaine

### Organisation et sécurité

| Entité | Attributs clés |
|---|---|
| `Organization` | name, legal_name, country, status |
| `Hotel` | organization_id, code, name, legal_name, address, phone, email, logo_url, legal_ids (JSON), currency_code, timezone, language, business_date, status |
| `HotelSetting` | hotel_id, key, value (JSON) — prix TTC/HT, heures check-in/out, politiques de garantie, inspection obligatoire, plafonds de remise, paramètres de clôture, formats de numérotation… |
| `User` | organization_id, email (unique), password_hash, first_name, last_name, phone, language, is_super_admin, status, failed_login_count, locked_until, last_login_at |
| `Role` | organization_id, code, name, is_system |
| `Permission` | code (unique), module, description |
| `RolePermission` | role_id, permission_id |
| `UserHotelRole` | user_id, hotel_id, role_id — unique (user_id, hotel_id, role_id) |
| `RefreshToken` | user_id, token_hash, family_id, expires_at, revoked_at, ip, user_agent |
| `PasswordResetToken` | user_id, token_hash, expires_at, used_at |
| `AuthEvent` | user_id, email, event (LOGIN_SUCCESS, LOGIN_FAILURE…), ip, user_agent, at |

### Référentiels et paramétrage

| Entité | Attributs clés |
|---|---|
| `Currency` | code (ISO 4217), name, decimals |
| `ExchangeRate` | hotel_id, from_currency, to_currency, rate, valid_date, source |
| `Tax` | hotel_id, code, name, type (PERCENTAGE / FIXED_PER_PERSON_NIGHT / FIXED_PER_UNIT), rate, amount, is_vat, applies_to (catégories), exemptions (JSON), valid_from, valid_to |
| `PaymentMethod` | hotel_id, code, name, type, provider, requires_cash_session, requires_reference, active |
| `ReservationChannel` | hotel_id, code, name, commission_rate, active |
| `CancellationPolicy` | hotel_id, name, free_until_hours, penalty_type, penalty_value, no_show_penalty_type, no_show_penalty_value |
| `Amenity` | hotel_id, name, icon |
| `ReasonCode` | hotel_id, domain (CANCELLATION, DISCOUNT, ROOM_CHANGE, ADJUSTMENT…), code, label |
| `NumberSequence` | hotel_id, document_type, year, last_value — unique (hotel_id, document_type, year) |

### Chambres

| Entité | Attributs clés |
|---|---|
| `RoomType` | hotel_id, code, name, description, base_occupancy, max_occupancy, max_adults, max_children, base_price, sort_order, active |
| `RoomTypeAmenity` | room_type_id, amenity_id |
| `RoomTypePhoto` | room_type_id, file_url, sort_order |
| `Room` | hotel_id, room_type_id, number, floor, building, features (JSON), occupancy_status, housekeeping_status, notes, active — unique (hotel_id, number) |
| `RoomStatusHistory` | room_id, dimension, old_value, new_value, reason, user_id, at |
| `RoomBlock` | hotel_id, room_id, type, start_date, end_date, reason, maintenance_ticket_id, created_by, released_at, released_by |

### Clients et entreprises

| Entité | Attributs clés |
|---|---|
| `Customer` | hotel_id*, title, first_name, last_name, birth_date, birth_place, nationality, gender, profession, phone, email, address, country, company_id, preferences (JSON), is_vip, is_blacklisted, blacklist_reason, marketing_consent, marketing_consent_at |
| `CustomerDocument` | customer_id, type, number (chiffré), issuing_country, issue_date, expiry_date, file_url (chiffré) |
| `Company` | hotel_id*, legal_name, short_name, rccm, tax_id, address, billing_address, phone, email, payment_terms_days, credit_limit, status |
| `CompanyContact` | company_id, name, position, phone, email |
| `Complaint` (MVP 2) | customer_id, stay_id, category, description, status, resolution |

\* Clients et entreprises sont rattachés à l'hôtel au MVP ; un partage au niveau de l'organisation est possible en évolution (multi-hôtel avancé).

### Tarification et promotions

| Entité | Attributs clés |
|---|---|
| `RatePlan` | hotel_id, code, name, type, min_nights, max_nights, days_of_week, cancellation_policy_id, included_services (JSON), valid_from, valid_to, active |
| `Rate` | rate_plan_id, room_type_id, start_date, end_date, days_of_week, price, extra_adult_price, extra_child_price, day_use_price |
| `CompanyRatePlan` | company_id, rate_plan_id, valid_from, valid_to |
| `Promotion` (MVP 2) | hotel_id, code, discount_type, discount_value, booking_from/to, stay_from/to, room_type_ids, max_uses, max_uses_per_customer, min_nights, combinable |
| `PromotionUsage` | promotion_id, reservation_id, customer_id, discount_amount |

### Réservations et séjours

| Entité | Attributs clés |
|---|---|
| `Reservation` | hotel_id, number, customer_id, company_id, channel_id, external_ref, guarantee_type, option_expires_at, cancellation_policy (JSON figé), comments, status (calculé, stocké) |
| `ReservationRoom` | reservation_id, hotel_id, room_type_id, room_id (nullable), arrival_date, departure_date, adults, children, children_ages, rate_plan_id, status, cancelled_at, cancel_reason_id, no_show_at |
| `ReservationNight` | reservation_room_id, night_date, price, discount_amount, rate_plan_id, is_manual_price, manual_price_reason — unique (reservation_room_id, night_date) |
| `ReservationGuest` | reservation_room_id, customer_id, is_primary |
| `Stay` | hotel_id, reservation_room_id (unique), actual_arrival_at, actual_departure_at, checked_in_by, checked_out_by, status |
| `StayRoomSegment` | stay_id, room_id, start_date, end_date, reason_id |

### Finance

| Entité | Attributs clés |
|---|---|
| `Folio` | hotel_id, number, type, reservation_id, reservation_room_id, stay_id, customer_id, company_id, status, balance (cache) |
| `FolioCharge` | folio_id, business_date, category, source_type, source_id, description, quantity, unit_price, discount_amount, amount_excl_tax, amount_incl_tax, reversed_charge_id, reason, status, invoice_id, stay_id + night_date (nuitées) |
| `FolioChargeTax` | folio_charge_id, tax_id, tax_code, rate, base_amount, tax_amount |
| `FolioTransfer` | from_folio_id, to_folio_id, charge_id, reason, user_id |
| `Invoice` | hotel_id, type (INVOICE / PROFORMA / CREDIT_NOTE / RECEIPT), number, status, issue_date, business_date, folio_id, customer_snapshot (JSON), company_snapshot (JSON), hotel_snapshot (JSON), total_excl_tax, total_vat, total_other_tax, total_incl_tax, amount_paid, balance, credited_invoice_id, pdf_url, pdf_sha256 |
| `InvoiceLine` | invoice_id, folio_charge_id, description, quantity, unit_price, discount_amount, amount_excl_tax, tax_rate, amount_incl_tax |
| `InvoiceTaxSummary` | invoice_id, tax_code, rate, base_amount, tax_amount |
| `Payment` | hotel_id, reference, external_reference, folio_id, invoice_id, payment_method_id, kind (PAYMENT / REFUND), original_payment_id, amount, currency_code, exchange_rate, amount_hotel_currency, cash_session_id, business_date, status, reason |
| `CashRegister` | hotel_id, code, name, outlet, active |
| `CashSession` | cash_register_id, user_id, business_date, opened_at, opening_amount, closed_at, expected_amount, counted_amount, difference, justification, status |
| `CashMovement` | cash_session_id, type, amount, payment_id, expense_id, reason, user_id, at |
| `CashCount` | cash_session_id, denomination, quantity |
| `NightAudit` | hotel_id, business_date, status, started_by, started_at, finished_at |
| `NightAuditStep` | night_audit_id, step, status, started_at, finished_at, details (JSON) |
| `Expense` (MVP 2) | hotel_id, category_id, amount, currency_code, supplier_id, expense_date, payment_method_id, attachment_url, cash_movement_id, status, comment, created_by, validated_by |
| `ExpenseCategory` | hotel_id, name, accounting_code, active |

### Services, restaurant, housekeeping, maintenance

| Entité | Attributs clés |
|---|---|
| `ServiceCategory` | hotel_id, name |
| `Service` | hotel_id, category_id, code, name, charge_category, price, pricing_unit, availability, active |
| `Outlet` (MVP 3) | hotel_id, code, name, type (RESTAURANT, BAR, ROOM_SERVICE), cash_register_id |
| `RestaurantTable` | outlet_id, number, capacity, area |
| `ProductCategory` | outlet_id, name |
| `Product` | hotel_id, category_id, name, price, charge_category, stock_item_id, stock_quantity_per_unit, active |
| `Order` | outlet_id, number, table_id, waiter_id, covers, customer_id, stay_id, status, total, folio_id |
| `OrderItem` | order_id, product_id, quantity, unit_price, discount_amount, tax_amount, total, status, void_reason |
| `HousekeepingTask` (MVP 2) | hotel_id, room_id, business_date, type (DEPARTURE, STAYOVER, ARRIVAL_PREP, DEEP_CLEAN), priority, assigned_to, status, started_at, finished_at, inspected_by, inspection_result, notes |
| `Equipment` (MVP 3) | hotel_id, room_id, name, category, serial_number, install_date |
| `MaintenanceTicket` (MVP 3) | hotel_id, number, room_id, equipment_id, category, description, priority, is_blocking, requested_by, assigned_to, status, opened_at, started_at, resolved_at, closed_at, resolution, cost |

### Stocks et achats (MVP 3)

| Entité | Attributs clés |
|---|---|
| `StockItem` | hotel_id, name, category, unit, min_quantity, quantity (cache), average_cost, default_supplier_id, active |
| `StockMovement` | stock_item_id, type, quantity, unit_cost, source_type, source_id, reason, user_id, at |
| `Inventory` / `InventoryLine` | hotel_id, date, status / stock_item_id, expected_qty, counted_qty |
| `Supplier` | hotel_id, legal_name, tax_id, rccm, address, phone, email, payment_terms_days |
| `SupplierContact` | supplier_id, name, phone, email |
| `Purchase` / `PurchaseItem` | supplier_id, number, status, order_date / stock_item_id, quantity, unit_price, received_quantity |
| `SupplierInvoice` | supplier_id, purchase_id, number, date, amount, status |
| `SupplierPayment` | supplier_invoice_id, amount, payment_method_id, cash_movement_id, date |

### Transverse

| Entité | Attributs clés |
|---|---|
| `AuditLog` | voir §32.1 (partitionnement mensuel) |
| `IdempotencyKey` | hotel_id, user_id, key, request_hash, response_status, response_body, created_at |
| `OutboxEvent` | géré par le registre d'événements Spring Modulith (`event_publication`) |
| `Notification` (MVP 2) | hotel_id, user_id / role_id, type, title, body, link, read_at |
| `FileObject` | hotel_id, bucket, key, content_type, size, sha256, uploaded_by |

## 33.3 Relations principales

```text
Organization 1─n Hotel
Hotel 1─n Room, RoomType, User (via UserHotelRole), RatePlan, Service, CashRegister, Tax
RoomType 1─n Room
Customer 1─n Reservation (titulaire) ; n─n ReservationRoom (occupants)
Company 1─n Customer ; 1─n Reservation
Reservation 1─n ReservationRoom 1─n ReservationNight
ReservationRoom 1─0..1 Stay 1─n StayRoomSegment
ReservationRoom 1─n Folio ; Stay 1─n Folio
Folio 1─n FolioCharge, 1─n Payment, 1─n Invoice
Invoice 1─n InvoiceLine ; Invoice (avoir) n─1 Invoice (d'origine)
Payment (remboursement) n─1 Payment (d'origine)
CashRegister 1─n CashSession 1─n CashMovement
```

## 33.4 Contraintes d'intégrité

| Domaine | Contrainte | Mise en œuvre |
|---|---|---|
| Réservation | Pas de chevauchement sur une chambre | Contrainte `EXCLUDE` (§13.2) |
| Réservation | Départ ≥ arrivée ; capacité respectée | `CHECK` + validation métier |
| Blocage | Pas de chevauchement avec une réservation attribuée | Validation transactionnelle + `EXCLUDE` entre blocages |
| Nuitée | Une seule charge d'hébergement par séjour et par nuit | Index unique partiel (stay_id, night_date) |
| Paiement | Exactement un contexte (folio XOR facture) | `CHECK ((folio_id IS NULL) <> (invoice_id IS NULL))` |
| Paiement | Référence unique ; référence externe unique par moyen | Index uniques |
| Remboursement | Référence le paiement d'origine ; montant ≤ restant | `CHECK` + validation transactionnelle |
| Facture | Numéro unique, continu ; immuable après émission | Compteur verrouillé + trigger refusant `UPDATE` sur les champs financiers d'une facture `ISSUED` |
| Charges | Aucune écriture sur une date d'exploitation clôturée | Validation + trigger |
| Caisse | Une session ouverte par caisse et par utilisateur | Index uniques partiels `WHERE status = 'OPEN'` |
| Caisse | Mouvement rattaché à une session ouverte | Validation transactionnelle |
| Stock | Quantité ≥ 0 (si paramètre) | `CHECK` + verrou sur l'article |
| Multi-hôtel | Données isolées par hôtel | Filtre applicatif + RLS PostgreSQL |
| Audit | Ajout seul | Droits SQL |

## 33.5 Index principaux

* `reservation_room (hotel_id, arrival_date)`, `(hotel_id, departure_date)`, index GiST sur la période.
* `customer` : index trigramme (`pg_trgm`) sur nom/prénom non accentués, index sur téléphone, email.
* `folio_charge (folio_id)`, `(hotel_id, business_date)`.
* `payment (hotel_id, business_date)`, `(folio_id)`.
* `audit_log (hotel_id, created_at)`, `(entity_type, entity_id)`.

---

# 34. API REST

## 34.1 Conventions

* Base : `/api/v1`. Ressources d'un hôtel : `/api/v1/hotels/{hotelId}/…` (l'accès à l'hôtel est vérifié à chaque requête).
* JSON, champs en `camelCase`, dates ISO 8601, montants en chaînes décimales (`"45000.00"`) pour éviter toute perte de précision.
* Pagination : `?page=1&size=25` (taille max. 100) ; réponse `meta: { page, size, total }`.
* Tri `?sort=arrivalDate,desc` ; filtres explicites par ressource.
* Actions métier exprimées en sous-ressources : `POST …/reservations/{id}/cancel`.
* Documentation OpenAPI générée automatiquement, publiée en DEV/TEST.

## 34.2 Format des réponses

Succès :

```json
{ "success": true, "data": { }, "meta": { } }
```

Erreur :

```json
{
  "success": false,
  "error": {
    "code": "ROOM_NOT_AVAILABLE",
    "message": "La chambre sélectionnée n'est pas disponible pour les dates demandées.",
    "details": [ { "field": "roomId", "issue": "OVERLAP", "conflictReservation": "RES-DKR01-2026-000045" } ]
  },
  "timestamp": "2026-10-01T10:00:00Z",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736"
}
```

Erreurs centralisées dans un `@RestControllerAdvice` ; les erreurs techniques internes ne sont jamais exposées (message générique + traceId).

## 34.3 Codes HTTP

| Code | Usage |
|---|---|
| 200 / 201 / 204 | Succès / création / sans contenu |
| 400 | Requête mal formée |
| 401 | Non authentifié |
| 403 | Permission insuffisante ou hôtel non autorisé |
| 404 | Ressource inexistante (ou d'un autre hôtel) |
| 409 | Conflit : disponibilité, statut, version, date clôturée |
| 422 | Validation métier échouée |
| 429 | Limite de requêtes dépassée |
| 500 | Erreur interne |

## 34.4 Catalogue d'erreurs métier (extrait)

`ROOM_NOT_AVAILABLE`, `ROOM_TYPE_NOT_AVAILABLE`, `ROOM_NOT_CLEAN`, `ROOM_BLOCKED`, `CAPACITY_EXCEEDED`, `RESERVATION_INVALID_STATUS`, `OPTION_EXPIRED`, `BUSINESS_DATE_CLOSED`, `NIGHT_AUDIT_IN_PROGRESS`, `PENDING_DEPARTURES`, `FOLIO_BALANCE_NOT_ZERO`, `INVOICE_LOCKED`, `CHARGE_ALREADY_INVOICED`, `DISCOUNT_LIMIT_EXCEEDED`, `CREDIT_LIMIT_EXCEEDED`, `CASH_SESSION_NOT_OPEN`, `CASH_SESSION_ALREADY_OPEN`, `REFUND_EXCEEDS_PAYMENT`, `PAYMENT_NOT_CANCELLABLE`, `STOCK_INSUFFICIENT`, `VERSION_CONFLICT`, `IDEMPOTENCY_KEY_REUSED`, `PERMISSION_DENIED`, `ID_DOCUMENT_REQUIRED`.

## 34.5 Endpoints MVP 1

```http
# Authentification
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/password/forgot
POST   /api/v1/auth/password/reset
PUT    /api/v1/auth/password
GET    /api/v1/me
GET    /api/v1/me/sessions
DELETE /api/v1/me/sessions/{id}

# Administration
GET|POST        /api/v1/users
GET|PUT         /api/v1/users/{id}
POST            /api/v1/users/{id}/disable
GET|POST        /api/v1/roles
PUT             /api/v1/roles/{id}
GET             /api/v1/permissions
GET|POST        /api/v1/hotels
GET|PUT         /api/v1/hotels/{hotelId}
GET|PUT         /api/v1/hotels/{hotelId}/settings
GET|POST|PUT    /api/v1/hotels/{hotelId}/taxes
GET|POST|PUT    /api/v1/hotels/{hotelId}/payment-methods
GET|POST|PUT    /api/v1/hotels/{hotelId}/channels

# Chambres
GET|POST        /api/v1/hotels/{hotelId}/room-types
GET|POST        /api/v1/hotels/{hotelId}/rooms
GET|PUT         /api/v1/hotels/{hotelId}/rooms/{id}
PUT             /api/v1/hotels/{hotelId}/rooms/{id}/housekeeping-status
GET|POST        /api/v1/hotels/{hotelId}/room-blocks
POST            /api/v1/hotels/{hotelId}/room-blocks/{id}/release
GET             /api/v1/hotels/{hotelId}/availability
GET             /api/v1/hotels/{hotelId}/planning?from=…&to=…

# Clients / entreprises
GET|POST        /api/v1/hotels/{hotelId}/customers
GET|PUT         /api/v1/hotels/{hotelId}/customers/{id}
GET             /api/v1/hotels/{hotelId}/customers/{id}/history
GET|POST        /api/v1/hotels/{hotelId}/companies
GET|PUT         /api/v1/hotels/{hotelId}/companies/{id}

# Tarifs
GET|POST|PUT    /api/v1/hotels/{hotelId}/rate-plans
GET|POST|PUT    /api/v1/hotels/{hotelId}/rate-plans/{id}/rates
POST            /api/v1/hotels/{hotelId}/pricing/quote

# Réservations
GET|POST        /api/v1/hotels/{hotelId}/reservations
GET|PUT         /api/v1/hotels/{hotelId}/reservations/{id}
POST            /api/v1/hotels/{hotelId}/reservations/{id}/confirm
POST            /api/v1/hotels/{hotelId}/reservations/{id}/cancel
POST            /api/v1/hotels/{hotelId}/reservations/{id}/rooms
PUT             /api/v1/hotels/{hotelId}/reservations/{id}/rooms/{lineId}
POST            /api/v1/hotels/{hotelId}/reservations/{id}/rooms/{lineId}/assign
POST            /api/v1/hotels/{hotelId}/reservations/{id}/rooms/{lineId}/cancel
POST            /api/v1/hotels/{hotelId}/reservations/{id}/rooms/{lineId}/no-show
POST            /api/v1/hotels/{hotelId}/reservations/{id}/rooms/{lineId}/check-in
POST            /api/v1/hotels/{hotelId}/walk-ins

# Séjours et folios
GET             /api/v1/hotels/{hotelId}/stays?status=IN_HOUSE
GET             /api/v1/hotels/{hotelId}/stays/{id}
POST            /api/v1/hotels/{hotelId}/stays/{id}/room-change
POST            /api/v1/hotels/{hotelId}/stays/{id}/extend
POST            /api/v1/hotels/{hotelId}/stays/{id}/early-departure
POST            /api/v1/hotels/{hotelId}/stays/{id}/check-out
GET|POST        /api/v1/hotels/{hotelId}/folios
GET             /api/v1/hotels/{hotelId}/folios/{id}
POST            /api/v1/hotels/{hotelId}/folios/{id}/charges
POST            /api/v1/hotels/{hotelId}/folios/{id}/charges/{chargeId}/reverse
POST            /api/v1/hotels/{hotelId}/folios/{id}/transfers

# Facturation et paiements
GET             /api/v1/hotels/{hotelId}/invoices
POST            /api/v1/hotels/{hotelId}/invoices            (émission depuis un folio)
POST            /api/v1/hotels/{hotelId}/invoices/proforma
GET             /api/v1/hotels/{hotelId}/invoices/{id}
GET             /api/v1/hotels/{hotelId}/invoices/{id}/pdf
POST            /api/v1/hotels/{hotelId}/invoices/{id}/send
POST            /api/v1/hotels/{hotelId}/invoices/{id}/credit-notes
GET|POST        /api/v1/hotels/{hotelId}/payments
POST            /api/v1/hotels/{hotelId}/payments/{id}/cancel
POST            /api/v1/hotels/{hotelId}/payments/{id}/refunds
GET             /api/v1/hotels/{hotelId}/payments/{id}/receipt

# Caisse
GET             /api/v1/hotels/{hotelId}/cash-registers
POST            /api/v1/hotels/{hotelId}/cash-sessions
GET             /api/v1/hotels/{hotelId}/cash-sessions/current
POST            /api/v1/hotels/{hotelId}/cash-sessions/{id}/movements
POST            /api/v1/hotels/{hotelId}/cash-sessions/{id}/close

# Clôture journalière
GET             /api/v1/hotels/{hotelId}/night-audit/precheck
POST            /api/v1/hotels/{hotelId}/night-audit
GET             /api/v1/hotels/{hotelId}/night-audit/{id}

# Services
GET|POST|PUT    /api/v1/hotels/{hotelId}/services

# Dashboard, rapports, audit
GET             /api/v1/hotels/{hotelId}/dashboard
GET             /api/v1/hotels/{hotelId}/reports/{reportCode}?format=json|pdf|xlsx|csv
GET             /api/v1/audit-logs
```

---

# 35. DASHBOARD ET REPORTING

## 35.1 Définition des indicateurs

| Indicateur | Formule |
|---|---|
| Chambres disponibles à la vente | Chambres actives − chambres `OUT_OF_ORDER` |
| Chambres occupées | Séjours en maison à la date |
| **Taux d'occupation (TO)** | Chambres occupées ÷ chambres disponibles à la vente × 100 |
| **ADR** | CA hébergement HT ÷ chambres occupées |
| **RevPAR** | CA hébergement HT ÷ chambres disponibles à la vente |
| **CA (comptabilisé)** | Σ charges HT par date d'exploitation (hors taxes de séjour) |
| **Encaissements** | Σ paiements nets par date d'exploitation |
| **Impayés** | Σ soldes débiteurs des folios des séjours sortis + factures entreprise non soldées |

## 35.2 Dashboard

* **MVP 1** : date d'exploitation, TO du jour, chambres libres / occupées / sales / hors service, arrivées et départs (prévus / effectués), clients en maison, CA du jour et du mois, encaissements du jour par moyen de paiement, impayés.
* **MVP 2** : graphiques (CA quotidien et mensuel, TO, revenus par type de chambre, revenus par service, répartition des moyens de paiement, évolution des réservations, canaux), ADR, RevPAR.

## 35.3 Rapports

| Rapport | Contenu | Phase |
|---|---|---|
| Arrivées / départs du jour | Liste avec chambres, soldes, statuts | MVP 1 |
| Clients en maison | Chambre, client, dates, solde | MVP 1 |
| **Main courante** | Charges et paiements par date d'exploitation, par chambre, par catégorie | MVP 1 |
| Rapport de caisse | Ouverture, mouvements, clôture, écarts, par moyen de paiement | MVP 1 |
| Occupation | Capacité, disponibles, occupées, hors service, TO par jour | MVP 1 |
| Rapport de secours | État complet de l'hôtel à imprimer en cas de panne | MVP 1 |
| Registre de police | Fiches des occupants sur une période | MVP 1 |
| Financier | CA par catégorie, taxes collectées, paiements, remboursements, impayés, dépenses | MVP 2 |
| Réservations | Confirmées, annulées, no-show, durée moyenne de séjour, délai de réservation, canaux | MVP 2 |
| Clients | Nouveaux, récurrents, nationalités, entreprises, statistiques de séjour | MVP 2 |
| Housekeeping | Productivité, temps moyen, inspections | MVP 2 |
| Taxes | TVA collectée par taux, taxe de séjour (déclarations) | MVP 2 |
| Restaurant, stocks, achats, maintenance | | MVP 3 |
| Balance âgée entreprises | | MVP 3 |
| Export comptable | Écritures (journal ventes, encaissements) au format CSV paramétrable | MVP 3 |

Exports : PDF et CSV au MVP 1, Excel en MVP 2. Les rapports volumineux sont générés en tâche de fond et mis à disposition au téléchargement.

---

# 36. DONNÉES PERSONNELLES ET OBLIGATIONS LÉGALES

## 36.1 Protection des données

Le système doit permettre la conformité à la législation locale sur les données personnelles (au Sénégal : loi n° 2008-12 et Commission de Protection des Données Personnelles — CDP ; à adapter selon le pays).

* **Minimisation** : seuls les champs nécessaires sont obligatoires.
* **Pièces d'identité** : numéro et scan **chiffrés** au repos ; consultation tracée (`CUSTOMER_ID_DOCUMENT_VIEW`).
* **Durée de conservation paramétrable** : purge ou anonymisation automatique des pièces d'identité et des données des clients inactifs (ex. 5 ans), en préservant les documents financiers.
* **Droits des personnes** : export des données d'un client (accès), rectification, anonymisation sur demande (si aucune obligation légale de conservation).
* Consentement marketing explicite et horodaté.
* Aucune donnée personnelle réelle dans les environnements hors production.
* Les données personnelles ne figurent ni dans les URL ni dans les logs (masquage).

## 36.2 Fiche de police

* Générée au check-in pour chaque occupant adulte : identité, date et lieu de naissance, nationalité, profession, domicile, pièce d'identité, dates d'arrivée et de départ prévues, provenance et destination.
* Modèle paramétrable par pays.
* Registre consultable et exportable par période.

## 36.3 Facturation locale

Mentions légales et identifiants fiscaux paramétrables par pays (§19.4). Une veille réglementaire est nécessaire sur la facturation électronique et la certification des logiciels de facturation dans les pays cibles (voir §47).

---

# 37. INTERFACE UTILISATEUR

## 37.1 Menu principal

```text
Dashboard
Réception : Arrivées · Départs · Clients en maison · Walk-in
Réservations · Planning
Clients · Entreprises
Chambres · Housekeeping · Maintenance
Restaurant · Services
Facturation · Paiements · Caisse · Dépenses
Stocks · Fournisseurs
Rapports
Clôture journalière
Administration : Utilisateurs · Rôles · Audit · Paramètres
```

Les entrées sont affichées selon les permissions de l'utilisateur et la phase livrée.

## 37.2 Principes UX

* Minimiser les clics pour les actions fréquentes (check-in, encaissement, ajout de charge).
* **Bandeau permanent** : hôtel actif, date d'exploitation, session de caisse ouverte.
* **Recherche globale** (client, réservation, chambre, facture) accessible au clavier.
* Statuts affichés avec couleurs et libellés cohérents dans toute l'application.
* Confirmation explicite pour les opérations sensibles, avec récapitulatif de l'impact.
* Formats locaux : montants `146 000 XOF`, dates `JJ/MM/AAAA`.
* Responsive ; utilisable sur tablette à partir de 768 px (vues housekeeping et restaurant optimisées tablette).
* Accessibilité de base : contrastes, navigation au clavier, libellés de formulaires.

## 37.3 Écrans MVP 1 nécessitant une maquette avant développement

1. Planning (grille, glisser-déposer).
2. Création / modification de réservation (recherche de disponibilité intégrée).
3. Check-in (y compris walk-in).
4. Folio et check-out.
5. Encaissement et clôture de caisse.
6. Clôture journalière (pré-contrôles et progression).
7. Dashboard.

---

# 38. EXIGENCES NON FONCTIONNELLES

| Catégorie | Exigence |
|---|---|
| Volumétrie (H6) | 20 hôtels, 300 chambres par hôtel, 1 million de réservations, 10 millions de lignes d'audit sans dégradation |
| Utilisateurs simultanés | 50 par hôtel, 500 au total |
| Temps de réponse (p95) | Opérations courantes < 500 ms ; recherche de disponibilité < 1 s ; planning 30 jours / 300 chambres < 2 s ; rapports interactifs < 5 s (au-delà : génération asynchrone) |
| Disponibilité | 99,5 % mensuel en heures d'exploitation ; maintenance planifiée hors heures de pointe |
| Sauvegarde | RPO ≤ 1 h (archivage continu des journaux PostgreSQL / PITR) ; RTO ≤ 4 h |
| Conservation des sauvegardes | Quotidiennes 30 jours, mensuelles 12 mois ; chiffrées ; copie hors site |
| Test de restauration | Mensuel, documenté |
| Mode dégradé (H3) | Rapport de secours PDF après chaque clôture journalière et à la demande ; consultation en lecture seule des dernières données en cache navigateur (MVP 2) |
| Navigateurs | Deux dernières versions de Chrome, Edge, Firefox, Safari |
| Langues | Français (MVP 1), anglais (MVP 2) ; tous les textes externalisés (i18n) |
| Fuseau horaire | Celui de l'hôtel pour les dates métier ; UTC en base pour les horodatages |
| Maintenabilité | Code modulaire, couverture de tests ≥ 80 % sur les modules métier, documentation API à jour, ADR |
| Sécurité | Référentiel OWASP ASVS niveau 2 ; test d'intrusion avant mise en production |

---

# 39. SÉCURITÉ

* HTTPS obligatoire (TLS 1.2+), HSTS.
* Hachage Argon2id ; JWT courts + jetons de rafraîchissement rotatifs (§7.2).
* RBAC contrôlé côté serveur sur chaque endpoint (Spring Security, `@PreAuthorize`) ; contrôle d'accès à l'hôtel sur chaque requête ; RLS PostgreSQL en défense en profondeur.
* Validation serveur systématique de toutes les entrées (Bean Validation).
* Requêtes paramétrées / ORM (protection contre l'injection SQL).
* Protection XSS : échappement par défaut (React), Content Security Policy.
* CORS restreint aux origines déclarées par environnement.
* Limitation de débit : 100 requêtes/min par utilisateur, 10 tentatives de connexion/min par IP.
* Secrets en variables d'environnement / gestionnaire de secrets, **jamais dans le code** ; analyse automatique des secrets dans la CI.
* Chiffrement au repos des données sensibles (pièces d'identité) et des sauvegardes.
* Analyse des dépendances vulnérables (OWASP Dependency-Check) dans la CI.
* En-têtes de sécurité (X-Content-Type-Options, Referrer-Policy, frame-ancestors).
* Téléversement de fichiers : types et tailles contrôlés, stockage hors de la racine web, URL signées à durée limitée.

---

# 40. ARCHITECTURE TECHNIQUE

## 40.1 Stack proposée (H1 — à valider)

| Couche | Choix proposé |
|---|---|
| Frontend | React, TypeScript, Vite, React Router, TanStack Query (état serveur), Zustand (état client), React Hook Form + Zod, bibliothèque de composants (MUI ou Mantine), i18next |
| Planning | Composant de grille développé sur mesure (éviter les composants sous licence payante) |
| Backend | Java 21 (LTS), Spring Boot 4.1, Maven ; Spring Web, Spring Security, Spring Data JPA (Hibernate), Bean Validation, Spring Modulith, MapStruct |
| Sécurité | Spring Security (OAuth2 Resource Server pour la validation JWT), `Argon2PasswordEncoder`, Bucket4j (limitation de débit) |
| Accès aux données | Spring Data JPA / Hibernate, verrouillage optimiste `@Version` ; requêtes SQL natives ou jOOQ pour le planning et les rapports ; **migrations Flyway** en SQL pour les contraintes avancées (`EXCLUDE`, RLS, triggers) |
| Calculs monétaires | `BigDecimal` ; sérialisation JSON des montants en chaînes (Jackson) |
| Documentation API | springdoc-openapi ; génération du client TypeScript (openapi-generator ou orval) |
| Base de données | PostgreSQL 16+ (extensions `btree_gist`, `pg_trgm`, `pgcrypto`) |
| Jobs | JobRunr (persisté en PostgreSQL), ShedLock |
| Cache | Caffeine (local) ; Redis optionnel si plusieurs instances de l'API |
| Fichiers | Stockage objet compatible S3 (MinIO en local) |
| PDF | Gabarits Thymeleaf → PDF (OpenHTMLtoPDF) |
| Excel / CSV | Apache POI / Apache Commons CSV |
| Email | Spring Mail + gabarits Thymeleaf, SMTP (fournisseur transactionnel) ; Mailpit en local |
| Logs | Journalisation structurée native de Spring Boot (JSON logstash), MDC pour les identifiants de corrélation |
| Observabilité | Spring Boot Actuator, Micrometer, OpenTelemetry, Prometheus + Grafana, Sentry |
| Reverse proxy | Caddy ou Nginx |
| CI/CD | GitHub Actions (lint, tests, build, analyse de sécurité, images Docker) |

## 40.2 Backend — structure en couches

```text
API (contrôleurs, validation des entrées, sérialisation)
 ↓
Application (cas d'usage, transactions, permissions, idempotence)
 ↓
Domaine (entités, règles métier, calculs — sans dépendance technique)
 ↓
Infrastructure (repositories / ORM, PostgreSQL, stockage, email, fournisseurs de paiement)
```

Modules (un package Java par module, frontières vérifiées par Spring Modulith) : `auth`, `iam` (utilisateurs, rôles), `hotel` (paramétrage), `rooms`, `customers`, `companies`, `rates`, `reservations`, `availability`, `stays`, `folios`, `invoicing`, `payments`, `cash`, `night-audit`, `services`, `housekeeping`, `reporting`, `notifications`, `audit`, puis `pos`, `inventory`, `purchasing`, `maintenance`, `expenses`.

Règles : un module n'accède pas directement aux tables d'un autre module (il passe par son service public ou par des événements) ; le domaine ne dépend d'aucun framework.

## 40.3 Frontend — structure

```text
Pages (routes)
 ↓
Composants (réutilisables, design system)
 ↓
Hooks (logique d'écran, requêtes TanStack Query)
 ↓
Services API (client généré depuis OpenAPI)
 ↓
API REST
```

* Gestion centralisée des erreurs (interception, affichage des messages métier, traceId).
* Gestion des permissions (masquage des actions non autorisées).
* Génération et réutilisation des clés d'idempotence pour les actions critiques.

---

# 41. INFRASTRUCTURE ET EXPLOITATION

## 41.1 Environnements

`DEV` (local), `TEST` (intégration continue / recette interne), `PREPROD` (iso-production), `PROD`.

* Configuration externalisée par variables d'environnement.
* Bases de données, secrets et credentials **distincts** par environnement.
* Données de test générées (seed) ; jamais de copie de données de production non anonymisées.

## 41.2 Conteneurs

| Service | Rôle |
|---|---|
| `frontend` | Build statique servi par Nginx |
| `api` | Backend Spring Boot |
| `worker` | Même image que l'API lancée avec le profil Spring `worker` : tâches asynchrones (emails, PDF, rapports, clôture automatique, outbox) |
| `postgres` | Base de données |
| `redis` | Optionnel : cache et limitation de débit partagés lorsque l'API tourne sur plusieurs instances |
| `minio` | Stockage objet (local / sur site) |
| `proxy` | Reverse proxy HTTPS |
| `mailpit` | Capture des emails (DEV uniquement) |

Démarrage local : `docker compose up` ; un script d'initialisation crée l'organisation, un hôtel de démonstration, les rôles et permissions par défaut et un super administrateur.

## 41.3 Logs et monitoring

* Logs structurés JSON : application, authentification, API, base de données, paiement, réservation, erreurs.
* Identifiants de corrélation : `requestId`, `traceId`, et selon le contexte `hotelId`, `userId`, `reservationId`, `paymentId`, `invoiceId`.
* Conservation des logs applicatifs : 90 jours.
* Métriques : latence et taux d'erreur par endpoint, durée de la clôture journalière, files de jobs, connexions base.
* Alertes : erreurs 5xx, clôture journalière en échec ou non effectuée, sauvegarde en échec, espace disque.

## 41.4 Sauvegarde et restauration

Conformément au §38 : sauvegardes automatiques chiffrées, archivage continu (PITR), copie hors site, sauvegarde du stockage objet, procédure de restauration documentée et testée mensuellement.

## 41.5 Déploiement

Images Docker versionnées, migrations de base exécutées automatiquement et de manière rétro-compatible, déploiement PREPROD puis PROD après validation, procédure de retour arrière documentée.

---

# 42. TESTS

## 42.1 Tests unitaires (domaine)

Calcul des nuitées, résolution du prix (§8.2), taxes (pourcentage, forfaitaire, exonérations), remises et plafonds, arrondis par devise, soldes, disponibilité par chambre et par type, transitions de statut, politique d'annulation et pénalités, calcul de caisse, numérotation. **L'exemple du §8.7 est un test obligatoire.**

## 42.2 Tests d'intégration (Spring Boot Test + PostgreSQL réel via Testcontainers)

* Authentification, rafraîchissement, blocage après échecs.
* Permissions : chaque endpoint refuse un rôle non autorisé (403) et un hôtel non autorisé.
* **Concurrence** : deux réservations simultanées sur la même chambre et les mêmes dates → une seule réussit, l'autre reçoit 409.
* **Concurrence par type** : N+1 réservations simultanées sur un type ayant N chambres libres → N réussissent.
* **Idempotence** : deux POST de paiement avec la même clé → un seul paiement.
* Numérotation des factures sans trou sous charge concurrente.
* Clôture journalière rejouée → aucune nuitée en double.
* Isolation multi-hôtel : aucune donnée d'un autre hôtel accessible.

Outils : JUnit 5, AssertJ, Testcontainers, RestAssured, tests de structure Spring Modulith / ArchUnit.

## 42.3 Tests de bout en bout (navigateur, Playwright)

```text
Ouverture de caisse
 ↓
Création client
 ↓
Création réservation (avec acompte)
 ↓
Confirmation
 ↓
Check-in (fiche de police générée, chambre OCCUPIED)
 ↓
Ajout d'un service
 ↓
Clôture journalière (nuitée enregistrée)
 ↓
Check-out (paiement du solde, facture émise, chambre VACANT/DIRTY)
 ↓
Clôture de caisse (écart nul)
```

Autres scénarios : walk-in, annulation avec pénalité, no-show, changement de chambre, avoir.

## 42.4 Tests de charge

Outil : Gatling ou k6.


Recherche de disponibilité, création de réservation, planning : vérification des objectifs du §38 avant mise en production.

---

# 43. CRITÈRES D'ACCEPTATION GLOBAUX

| Domaine | Critères |
|---|---|
| Réservation | Une chambre disponible peut être réservée ; une chambre indisponible ne peut pas l'être ; les conflits sont détectés côté serveur, y compris en accès concurrent ; les prix par nuit sont figés |
| Check-in | Le client et les occupants sont enregistrés ; la fiche de police est générée ; la chambre passe automatiquement à `OCCUPIED` ; un check-in sur chambre sale est refusé sans permission |
| Séjour | Les services sont ajoutés au folio ; les nuitées sont enregistrées par la clôture journalière, sans doublon |
| Check-out | Le montant final est calculé conformément au §8 ; le solde doit être nul ; la facture est émise avec un numéro continu ; la chambre passe à `VACANT` / `DIRTY` |
| Facturation | Une facture émise ne peut pas être modifiée ; une correction produit un avoir |
| Paiement | Le paiement est enregistré, sa référence est unique, l'opération est auditée ; un double envoi ne crée qu'un paiement ; un remboursement référence le paiement d'origine |
| Caisse | L'ouverture est enregistrée ; un paiement espèces sans session ouverte est refusé ; les mouvements sont tracés ; la clôture calcule l'écart et exige une justification s'il est non nul |
| Clôture journalière | Bloquée tant que des départs ne sont pas traités ; date d'exploitation avancée ; date précédente verrouillée ; rapports archivés |
| Sécurité | Un utilisateur n'accède qu'aux fonctionnalités et aux hôtels autorisés, vérifié côté serveur |
| Audit | Toute opération sensible est consultable avec utilisateur, date, anciennes et nouvelles valeurs |

---

# 44. PHASAGE

## 44.1 MVP 1 — Cœur opérationnel

| Bloc | Contenu |
|---|---|
| Socle | Structure du projet, Docker, CI, authentification, utilisateurs, rôles, permissions, multi-hôtel (structure), **audit**, idempotence, logs, i18n (FR), gestion des erreurs, stockage des fichiers, email transactionnel |
| Paramétrage | Hôtel, taxes, moyens de paiement, canaux, politiques (annulation, garantie, horaires), motifs, numérotation |
| Chambres | Types, chambres, statuts à trois dimensions, historique, blocages manuels |
| Clients | Création, modification, recherche, doublons, pièces d'identité, historique |
| Entreprises | Fiche simple, rattachement, facture au nom de l'entreprise |
| Tarifs | Plan standard avec saisons et week-end, prix manuel |
| Réservations | Multi-chambres, par type ou chambre, options, modification, annulation avec pénalité, no-show |
| Disponibilité et planning | Recherche, grille, glisser-déposer, attribution |
| Séjours | Check-in, walk-in, fiche de police, folio, ajout de services, changement de chambre, prolongation, départ anticipé, check-out |
| Clôture journalière | Manuelle, complète (§17) |
| Facturation | Facture, reçu, pro forma, avoir, PDF, email |
| Paiements | Saisie manuelle de tous les moyens, annulation, remboursement |
| Caisse | Sessions, mouvements, clôture avec écart |
| Housekeeping | Statuts ménage manuels et automatiques |
| Dashboard et rapports | Indicateurs MVP 1 (§35.2) et rapports MVP 1 (§35.3), exports PDF et CSV |

## 44.2 MVP 2 — Exploitation complète

Housekeeping par tâches (vue tablette), dépenses, services inclus dans les tarifs, promotions, réclamations, fusion de clients, notifications internes, rapports complets et export Excel, paiements en devises étrangères, clôture journalière automatique, double authentification, anglais, mode dégradé en lecture seule, règles automatiques d'arrivée anticipée et de départ tardif.

## 44.3 MVP 3 — Activités annexes et corporate

Restaurant / bar (points de vente, commandes, transfert en chambre), stocks, fournisseurs et achats, maintenance (tickets, équipements), corporate avancé (tarifs négociés, comptes débiteurs, plafond de crédit, relevés, balance âgée), folio maître de groupe et routage des charges, tarifs avancés, export comptable.

## 44.4 Phase 4 — Intégrations

Mobile Money (Orange Money, Wave, MTN, Moov…), paiement par carte (passerelle), SMS, WhatsApp, OTA, réservation en ligne.

## 44.5 Phase 5 — Évolutions

Application mobile, portail client, fidélité, channel manager, BI, comptabilité, multi-hôtel avancé (clients partagés, consolidation), événements et salles.

Chaque phase est validée (recette + critères d'acceptation) avant de passer à la suivante.

---

# 45. BACKLOG MVP 1

## 45.1 Ordre de réalisation recommandé

| Itération | Contenu |
|---|---|
| 0 | Socle technique : dépôt, conventions, Docker, CI, squelettes Spring Boot et React, gestion des erreurs, logs, audit, idempotence, seed |
| 1 | Authentification, utilisateurs, rôles, permissions, sélection d'hôtel |
| 2 | Paramétrage hôtel, taxes, moyens de paiement, types de chambres, chambres, statuts, blocages |
| 3 | Clients, entreprises (simple), tarifs (plan standard), moteur de prix |
| 4 | Disponibilité, réservations (création, modification, annulation) |
| 5 | Planning |
| 6 | Check-in / walk-in, folio, services, fiche de police |
| 7 | Paiements, caisse, reçus |
| 8 | Clôture journalière, check-out, facturation, avoirs |
| 9 | Dashboard, rapports MVP 1, email, durcissement, tests de charge, recette |

## 45.2 User stories clés et critères d'acceptation

**US-RES-01 — Créer une réservation**
En tant que réceptionniste, je veux créer une réservation pour une ou plusieurs chambres afin de garantir l'hébergement d'un client.
* Étant donné une chambre libre du 10 au 13, quand je réserve du 10 au 13, alors la réservation est créée avec 3 nuits au prix du plan applicable, statut `CONFIRMED`, numéro `RES-…`.
* Étant donné une chambre réservée du 10 au 13, quand je tente de la réserver du 12 au 14, alors je reçois `ROOM_NOT_AVAILABLE` et rien n'est créé.
* Étant donné une chambre réservée du 10 au 13, quand je la réserve du 13 au 15, alors la réservation est acceptée (le départ libère la nuit).
* Quand j'envoie deux fois la même requête avec la même clé d'idempotence, une seule réservation existe.

**US-RES-04 — Annuler une réservation**
* Annulation dans le délai gratuit : aucune pénalité, acompte remboursable.
* Annulation hors délai : pénalité calculée selon la politique et portée au folio ; motif obligatoire ; audit enregistré.

**US-STAY-01 — Check-in**
* Étant donné une réservation confirmée arrivant à la date d'exploitation, une chambre `CLEAN`, quand je valide le check-in avec les pièces d'identité des adultes, alors le séjour est créé, la chambre est `OCCUPIED` et la fiche de police est disponible en PDF.
* Avec une chambre `DIRTY` et sans `CHECKIN_OVERRIDE_HOUSEKEEPING` : refus `ROOM_NOT_CLEAN`.
* Sans pièce d'identité pour un adulte (paramètre actif) : refus `ID_DOCUMENT_REQUIRED`.

**US-NA-01 — Clôture journalière**
* Avec un départ non traité : refus `PENDING_DEPARTURES` et liste des séjours concernés.
* Après clôture : une nuitée par séjour en maison au prix figé + taxe de séjour ; date d'exploitation + 1 ; aucune charge possible sur la date clôturée (`BUSINESS_DATE_CLOSED`).
* Clôture relancée après une panne à l'étape 5 : aucune nuitée en double.

**US-OUT-01 — Check-out**
* Folio de l'exemple §8.7 : total 146 000, payé 50 000, solde 96 000 ; après paiement de 96 000, la facture `FAC-…` est émise avec le récapitulatif TVA (118 645 HT / 21 355 TVA) et la taxe de séjour 6 000 ; chambre `VACANT` / `DIRTY`.
* Solde non nul sans permission : refus `FOLIO_BALANCE_NOT_ZERO`.

**US-PAY-02 — Rembourser un paiement**
* Remboursement ≤ montant restant : enregistré, lié au paiement d'origine, statut d'origine mis à jour, sortie de caisse si espèces.
* Remboursement > montant restant : refus `REFUND_EXCEEDS_PAYMENT`.
* Sans permission `PAYMENT_REFUND` : 403.

**US-CASH-02 — Clôturer la caisse**
* Fond 20 000, encaissements espèces 150 000, remboursement espèces 10 000 → théorique 160 000 ; compté 158 000 → écart −2 000, justification obligatoire ; session immuable ; rapport PDF généré.

**US-INV-03 — Émettre un avoir**
* Avoir total sur une facture émise : numéro `AV-…` continu, référence à la facture, facture d'origine `CANCELLED`, charges libérées pour une nouvelle facturation ; motif obligatoire ; audit.

Les autres user stories du MVP 1 seront détaillées au même format (description + critères Étant donné / Quand / Alors) en début de chaque itération.

---

# 46. LIVRABLES

## 46.1 Documentation

* Cahier des charges (ce document) et ses mises à jour.
* Décisions d'architecture (ADR).
* Modèle de données (schéma entité-relation généré + dictionnaire).
* Document des règles métier (extrait du §8 à §21, enrichi des cas limites).
* Maquettes des écrans du §37.3.
* Documentation API (OpenAPI).
* Guide d'installation, guide utilisateur par profil, documentation d'exploitation (sauvegarde, restauration, supervision, procédures d'incident).

## 46.2 Code

Frontend, backend, worker, migrations de base de données, données d'initialisation, tests (unitaires, intégration, E2E, charge), fichiers Docker, pipelines CI/CD.

## 46.3 Infrastructure

Configurations DEV, TEST, PREPROD, PROD ; procédures de déploiement, de retour arrière, de sauvegarde et de restauration.

---

# 47. POINTS OUVERTS

| # | Point | Responsable | Échéance |
|---|---|---|---|
| P1 | Stack backend : **validée — Java 21 / Spring Boot 4.1** | Porteur du projet | Clos |
| P2 | Valider le mode d'hébergement et le niveau de fonctionnement sans internet (H2, H3) | Porteur du projet | Avant l'itération 0 |
| P3 | Confirmer le pays du premier hôtel, les taux de TVA applicables à l'hébergement et à la restauration, le montant et les exonérations de la taxe de séjour | Porteur du projet / comptable | Avant l'itération 3 |
| P4 | Obtenir le modèle officiel de fiche de police du pays cible | Porteur du projet | Avant l'itération 6 |
| P5 | Vérifier les obligations de facturation électronique / certification du logiciel de facturation dans le pays cible | Comptable / conseil | Avant l'itération 8 |
| P6 | Définir la politique d'annulation, de garantie et de walk-in par défaut de l'hôtel pilote | Direction de l'hôtel | Avant l'itération 4 |
| P7 | Fixer les plafonds de remise par rôle | Direction de l'hôtel | Avant l'itération 3 |
| P8 | Confirmer la volumétrie et le nombre d'utilisateurs (H6) | Porteur du projet | Avant l'itération 0 |
| P9 | Équipe de développement et calendrier cible du MVP 1 | Porteur du projet | Avant l'itération 0 |
| P10 | Produire les maquettes des écrans critiques (§37.3) | UX / porteur du projet | Avant l'itération 4 |

---

# 48. PRINCIPES ARCHITECTURAUX

1. **API First** — contrat OpenAPI avant l'implémentation des écrans.
2. **Security by Design** — contrôle d'accès serveur, chiffrement, moindre privilège.
3. **Audit by Design** — dès le MVP 1.
4. **Modularité** — monolithe modulaire, domaine indépendant du framework.
5. **Séparation frontend / backend**.
6. **Transactions financières atomiques**.
7. **Idempotence des opérations critiques**.
8. **Validation côté serveur** — le frontend n'est jamais une couche de sécurité.
9. **Intégrité garantie par la base** — contraintes d'exclusion, d'unicité et `CHECK`.
10. **Documents financiers immuables** — corrections par écriture inverse ou avoir.
11. **Configuration externalisée**.
12. **Observabilité**.
13. **Tests automatisés**.
14. **Multi-hôtel natif**.

---

# 49. CONCLUSION

Le projet consiste à développer un **PMS moderne, sécurisé, modulaire et évolutif**, adapté au marché d'Afrique de l'Ouest, couvrant le cycle complet de gestion d'un établissement hôtelier.

Cette version 2.0 complète la vision fonctionnelle de la v1.0 avec les éléments nécessaires au démarrage des développements : règles de calcul, modèle de données, cycle de la date d'exploitation, folio, contraintes d'intégrité, décisions d'architecture et phasage cohérent. Les hypothèses du §0.3 et les points ouverts du §47 doivent être validés aux échéances indiquées ; aucun d'eux ne bloque l'itération 0 (socle technique) si les hypothèses par défaut sont acceptées.

**Priorités : fiabilité des données + cohérence métier + sécurité + traçabilité + simplicité d'utilisation + évolutivité.**
